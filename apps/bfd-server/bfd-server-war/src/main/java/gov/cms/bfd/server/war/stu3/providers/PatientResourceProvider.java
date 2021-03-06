package gov.cms.bfd.server.war.stu3.providers;

import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.RequiredParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import gov.cms.bfd.model.rif.Beneficiary;
import gov.cms.bfd.model.rif.BeneficiaryHistory;
import gov.cms.bfd.model.rif.BeneficiaryHistory_;
import gov.cms.bfd.model.rif.Beneficiary_;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.NonUniqueResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.SingularAttribute;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.IdType;
import org.hl7.fhir.dstu3.model.Identifier;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.stereotype.Component;

/**
 * This FHIR {@link IResourceProvider} adds support for STU3 {@link Patient} resources, derived from
 * the CCW beneficiaries.
 */
@Component
public final class PatientResourceProvider implements IResourceProvider {
  /**
   * The {@link Identifier#getSystem()} values that are supported by {@link
   * #searchByIdentifier(TokenParam)}.
   */
  private static final List<String> SUPPORTED_HASH_IDENTIFIER_SYSTEMS =
      Arrays.asList(
          TransformerConstants.CODING_BBAPI_BENE_MBI_HASH,
          TransformerConstants.CODING_BBAPI_BENE_HICN_HASH,
          TransformerConstants.CODING_BBAPI_BENE_HICN_HASH_OLD);

  private EntityManager entityManager;
  private MetricRegistry metricRegistry;

  /** @param entityManager a JPA {@link EntityManager} connected to the application's database */
  @PersistenceContext
  public void setEntityManager(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  /** @param metricRegistry the {@link MetricRegistry} to use */
  @Inject
  public void setMetricRegistry(MetricRegistry metricRegistry) {
    this.metricRegistry = metricRegistry;
  }

  /** @see ca.uhn.fhir.rest.server.IResourceProvider#getResourceType() */
  @Override
  public Class<? extends IBaseResource> getResourceType() {
    return Patient.class;
  }

  /**
   * Adds support for the FHIR "read" operation, for {@link Patient}s. The {@link Read} annotation
   * indicates that this method supports the read operation.
   *
   * <p>Read operations take a single parameter annotated with {@link IdParam}, and should return a
   * single resource instance.
   *
   * @param patientId The read operation takes one parameter, which must be of type {@link IdType}
   *     and must be annotated with the {@link IdParam} annotation.
   * @param requestDetails a {@link RequestDetails} containing the details of the request URL, used
   *     to parse out pagination values
   * @return Returns a resource matching the specified {@link IdDt}, or <code>null</code> if none
   *     exists.
   */
  @Read(version = false)
  public Patient read(@IdParam IdType patientId, RequestDetails requestDetails) {
    if (patientId == null) throw new IllegalArgumentException();
    if (patientId.getVersionIdPartAsLong() != null) throw new IllegalArgumentException();

    String beneIdText = patientId.getIdPart();
    if (beneIdText == null || beneIdText.trim().isEmpty()) throw new IllegalArgumentException();

    List<String> includeIdentifiersValues = returnIncludeIdentifiersValues(requestDetails);

    CriteriaBuilder builder = entityManager.getCriteriaBuilder();
    CriteriaQuery<Beneficiary> criteria = builder.createQuery(Beneficiary.class);
    Root<Beneficiary> root = criteria.from(Beneficiary.class);

    if (hasHICN(includeIdentifiersValues))
      root.fetch(Beneficiary_.beneficiaryHistories, JoinType.LEFT);

    if (hasMBI(includeIdentifiersValues))
      root.fetch(Beneficiary_.medicareBeneficiaryIdHistories, JoinType.LEFT);

    criteria.select(root);
    criteria.where(builder.equal(root.get(Beneficiary_.beneficiaryId), beneIdText));

    Beneficiary beneficiary = null;
    Long beneByIdQueryNanoSeconds = null;
    Timer.Context timerBeneQuery =
        metricRegistry
            .timer(MetricRegistry.name(getClass().getSimpleName(), "query", "bene_by_id"))
            .time();
    try {
      beneficiary = entityManager.createQuery(criteria).getSingleResult();
    } catch (NoResultException e) {
      throw new ResourceNotFoundException(patientId);
    } finally {
      beneByIdQueryNanoSeconds = timerBeneQuery.stop();

      TransformerUtils.recordQueryInMdc(
          String.format("bene_by_id.include_%s", String.join("_", includeIdentifiersValues)),
          beneByIdQueryNanoSeconds,
          beneficiary == null ? 0 : 1);
    }

    // Null out the unhashed HICNs if we're not supposed to be returning them
    if (!hasHICN(includeIdentifiersValues)) {
      beneficiary.setHicnUnhashed(Optional.empty());
    }

    // Null out the unhashed MBIs if we're not supposed to be returning
    if (!hasMBI(includeIdentifiersValues)) {
      beneficiary.setMedicareBeneficiaryId(Optional.empty());
    }

    Patient patient =
        BeneficiaryTransformer.transform(metricRegistry, beneficiary, includeIdentifiersValues);
    return patient;
  }

  /**
   * Adds support for the FHIR "search" operation for {@link Patient}s, allowing users to search by
   * {@link Patient#getId()}.
   *
   * <p>The {@link Search} annotation indicates that this method supports the search operation.
   * There may be many different methods annotated with this {@link Search} annotation, to support
   * many different search criteria.
   *
   * @param logicalId a {@link TokenParam} (with no system, per the spec) for the {@link
   *     Patient#getId()} to try and find a matching {@link Patient} for
   * @param startIndex an {@link OptionalParam} for the startIndex (or offset) used to determine
   *     pagination
   * @param requestDetails a {@link RequestDetails} containing the details of the request URL, used
   *     to parse out pagination values
   * @return Returns a {@link List} of {@link Patient}s, which may contain multiple matching
   *     resources, or may also be empty.
   */
  @Search
  public Bundle searchByLogicalId(
      @RequiredParam(name = Patient.SP_RES_ID) TokenParam logicalId,
      @OptionalParam(name = "startIndex") String startIndex,
      RequestDetails requestDetails) {
    if (logicalId.getQueryParameterQualifier() != null)
      throw new InvalidRequestException(
          "Unsupported query parameter qualifier: " + logicalId.getQueryParameterQualifier());
    if (logicalId.getSystem() != null && !logicalId.getSystem().isEmpty())
      throw new InvalidRequestException(
          "Unsupported query parameter system: " + logicalId.getSystem());
    if (logicalId.getValueNotNull().isEmpty())
      throw new InvalidRequestException(
          "Unsupported query parameter value: " + logicalId.getValue());

    List<IBaseResource> patients;
    try {
      patients = Arrays.asList(read(new IdType(logicalId.getValue()), requestDetails));
    } catch (ResourceNotFoundException e) {
      patients = new LinkedList<>();
    }

    PagingArguments pagingArgs = new PagingArguments(requestDetails);
    Bundle bundle =
        TransformerUtils.createBundle(
            pagingArgs, "/Patient?", Patient.SP_RES_ID, logicalId.getValue(), patients);
    return bundle;
  }

  /**
   * Adds support for the FHIR "search" operation for {@link Patient}s, allowing users to search by
   * {@link Patient#getIdentifier()}. Specifically, the following criteria are supported:
   *
   * <ul>
   *   <li>Matching a {@link Beneficiary#getHicn()} hash value: when {@link TokenParam#getSystem()}
   *       matches one of the {@link #SUPPORTED_HASH_IDENTIFIER_SYSTEMS} entries.
   * </ul>
   *
   * <p>Searches that don't match one of the above forms are not supported.
   *
   * <p>The {@link Search} annotation indicates that this method supports the search operation.
   * There may be many different methods annotated with this {@link Search} annotation, to support
   * many different search criteria.
   *
   * @param identifier an {@link Identifier} {@link TokenParam} for the {@link
   *     Patient#getIdentifier()} to try and find a matching {@link Patient} for
   * @param startIndex an {@link OptionalParam} for the startIndex (or offset) used to determine
   *     pagination
   * @param requestDetails a {@link RequestDetails} containing the details of the request URL, used
   *     to parse out pagination values
   * @return Returns a {@link List} of {@link Patient}s, which may contain multiple matching
   *     resources, or may also be empty.
   */
  @Search
  public Bundle searchByIdentifier(
      @RequiredParam(name = Patient.SP_IDENTIFIER) TokenParam identifier,
      @OptionalParam(name = "startIndex") String startIndex,
      RequestDetails requestDetails) {
    if (identifier.getQueryParameterQualifier() != null)
      throw new InvalidRequestException(
          "Unsupported query parameter qualifier: " + identifier.getQueryParameterQualifier());

    if (!SUPPORTED_HASH_IDENTIFIER_SYSTEMS.contains(identifier.getSystem()))
      throw new InvalidRequestException("Unsupported identifier system: " + identifier.getSystem());

    List<IBaseResource> patients;
    try {
      switch (identifier.getSystem()) {
        case TransformerConstants.CODING_BBAPI_BENE_HICN_HASH:
        case TransformerConstants.CODING_BBAPI_BENE_HICN_HASH_OLD:
          patients = Arrays.asList(queryDatabaseByHicnHash(identifier.getValue(), requestDetails));
          break;
        case TransformerConstants.CODING_BBAPI_BENE_MBI_HASH:
          patients = Arrays.asList(queryDatabaseByMbiHash(identifier.getValue(), requestDetails));
          break;
        default:
          throw new InvalidRequestException(
              "Unsupported identifier system: " + identifier.getSystem());
      }

    } catch (NoResultException e) {
      patients = new LinkedList<>();
    }

    PagingArguments pagingArgs = new PagingArguments(requestDetails);
    Bundle bundle =
        TransformerUtils.createBundle(
            pagingArgs, "/Patient?", Patient.SP_IDENTIFIER, identifier.getValue(), patients);
    return bundle;
  }

  /**
   * @param hicnHash the {@link Beneficiary#getHicn()} hash value to match
   * @return a FHIR {@link Patient} for the CCW {@link Beneficiary} that matches the specified
   *     {@link Beneficiary#getHicn()} hash value
   * @throws NoResultException A {@link NoResultException} will be thrown if no matching {@link
   *     Beneficiary} can be found
   */
  private Patient queryDatabaseByHicnHash(String hicnHash, RequestDetails requestDetails) {
    return queryDatabaseByHash(
        hicnHash, "hicn", requestDetails, Beneficiary_.hicn, BeneficiaryHistory_.hicn);
  }

  /**
   * @param mbiHash the {@link Beneficiary#getMbiHash()} ()} hash value to match
   * @return a FHIR {@link Patient} for the CCW {@link Beneficiary} that matches the specified
   *     {@link Beneficiary#getMbiHash()} ()} hash value
   * @throws NoResultException A {@link NoResultException} will be thrown if no matching {@link
   *     Beneficiary} can be found
   */
  private Patient queryDatabaseByMbiHash(String mbiHash, RequestDetails requestDetails) {
    return queryDatabaseByHash(
        mbiHash, "mbi", requestDetails, Beneficiary_.mbiHash, BeneficiaryHistory_.mbiHash);
  }

  /**
   * @param hash the {@link Beneficiary} hash value to match
   * @param hashType a string to represent the hash type (used for logging purposes)
   * @param requestDetails
   * @param beneficiaryHashField the JPA location of the beneficiary hash field
   * @param beneficiaryHistoryHashField the JPA location of the beneficiary history hash field
   * @return a FHIR {@link Patient} for the CCW {@link Beneficiary} that matches the specified
   *     {@link Beneficiary} hash value
   * @throws NoResultException A {@link NoResultException} will be thrown if no matching {@link
   *     Beneficiary} can be found
   */
  private Patient queryDatabaseByHash(
      String hash,
      String hashType,
      RequestDetails requestDetails,
      SingularAttribute<Beneficiary, String> beneficiaryHashField,
      SingularAttribute<BeneficiaryHistory, String> beneficiaryHistoryHashField) {
    if (hash == null || hash.trim().isEmpty()) throw new IllegalArgumentException();

    /*
     * Beneficiaries' HICN/MBIs can change over time and those past HICN/MBIs may land in
     * BeneficiaryHistory records. Accordingly, we need to search for matching HICN/MBIs
     * in both the Beneficiary and the BeneficiaryHistory records.
     *
     * There's no sane way to do this in a single query with JPA 2.1, it appears: JPA
     * doesn't support UNIONs and it doesn't support subqueries in FROM clauses. That
     * said, the ideal query would look like this:
     *
     * SELECT     *
     * FROM       (
     *                            SELECT DISTINCT "beneficiaryId"
     *                            FROM            "Beneficiaries"
     *                            WHERE           "hicn" = :'hicn_hash'
     *                            UNION
     *                            SELECT DISTINCT "beneficiaryId"
     *                            FROM            "BeneficiariesHistory"
     *                            WHERE           "hicn" = :'hicn_hash') AS matching_benes
     * INNER JOIN "Beneficiaries"
     * ON         matching_benes."beneficiaryId" = "Beneficiaries"."beneficiaryId"
     * LEFT JOIN  "BeneficiariesHistory"
     * ON         "Beneficiaries"."beneficiaryId" = "BeneficiariesHistory"."beneficiaryId"
     * LEFT JOIN  "MedicareBeneficiaryIdHistory"
     * ON         "Beneficiaries"."beneficiaryId" = "MedicareBeneficiaryIdHistory"."beneficiaryId";
     *
     * ... with the returned columns and JOINs being dynamic, depending on
     * IncludeIdentifiers.
     *
     * In lieu of that, we run two queries: one to find HICN/MBI matches in
     * BeneficiariesHistory, and a second to find BENE_ID or HICN/MBI matches in
     * Beneficiaries (with all of their data, so we're ready to return the result).
     * This is bad and dumb but I can't find a better working alternative.
     *
     * (I'll just note that I did also try JPA/Hibernate native SQL queries but
     * couldn't get the joins or fetch groups to work with them.)
     *
     * If we want to fix this, we need to move identifiers out entirely to separate
     * tables: BeneficiaryHicns and BeneficiaryMbis. We could then safely query these
     * tables and join them back to Beneficiaries (and hopefully the optimizer will
     * play nice, too).
     */

    CriteriaBuilder builder = entityManager.getCriteriaBuilder();

    // First, find all matching hashes from BeneficiariesHistory.
    CriteriaQuery<String> beneHistoryMatches = builder.createQuery(String.class);
    Root<BeneficiaryHistory> beneHistoryMatchesRoot =
        beneHistoryMatches.from(BeneficiaryHistory.class);
    beneHistoryMatches.select(beneHistoryMatchesRoot.get(BeneficiaryHistory_.beneficiaryId));
    beneHistoryMatches.where(
        builder.equal(beneHistoryMatchesRoot.get(beneficiaryHistoryHashField), hash));
    List<String> matchingIdsFromBeneHistory = null;
    Long hicnsFromHistoryQueryNanoSeconds = null;
    Timer.Context beneHistoryMatchesTimer =
        metricRegistry
            .timer(
                MetricRegistry.name(
                    getClass().getSimpleName(),
                    "query",
                    "bene_by_" + hashType,
                    hashType + "s_from_beneficiarieshistory"))
            .time();
    try {
      matchingIdsFromBeneHistory = entityManager.createQuery(beneHistoryMatches).getResultList();
    } finally {
      hicnsFromHistoryQueryNanoSeconds = beneHistoryMatchesTimer.stop();
      TransformerUtils.recordQueryInMdc(
          "bene_by_" + hashType + "." + hashType + "s_from_beneficiarieshistory",
          hicnsFromHistoryQueryNanoSeconds,
          matchingIdsFromBeneHistory == null ? 0 : matchingIdsFromBeneHistory.size());
    }

    // Then, find all Beneficiary records that match the hash or those BENE_IDs.
    CriteriaQuery<Beneficiary> beneMatches = builder.createQuery(Beneficiary.class);
    Root<Beneficiary> beneMatchesRoot = beneMatches.from(Beneficiary.class);

    List<String> includeIdentifiersValues = returnIncludeIdentifiersValues(requestDetails);

    if (hasHICN(includeIdentifiersValues))
      beneMatchesRoot.fetch(Beneficiary_.beneficiaryHistories, JoinType.LEFT);

    if (hasMBI(includeIdentifiersValues))
      beneMatchesRoot.fetch(Beneficiary_.medicareBeneficiaryIdHistories, JoinType.LEFT);

    beneMatches.select(beneMatchesRoot);
    Predicate beneHashMatches = builder.equal(beneMatchesRoot.get(beneficiaryHashField), hash);
    if (!matchingIdsFromBeneHistory.isEmpty()) {
      Predicate beneHistoryHashMatches =
          beneMatchesRoot.get(Beneficiary_.beneficiaryId).in(matchingIdsFromBeneHistory);
      beneMatches.where(builder.or(beneHashMatches, beneHistoryHashMatches));
    } else {
      beneMatches.where(beneHashMatches);
    }
    List<Beneficiary> matchingBenes = null;
    Long benesByHashOrIdQueryNanoSeconds = null;
    Timer.Context timerHicnQuery =
        metricRegistry
            .timer(
                MetricRegistry.name(
                    getClass().getSimpleName(),
                    "query",
                    "bene_by_" + hashType,
                    "bene_by_" + hashType + "_or_id"))
            .time();
    try {
      matchingBenes = entityManager.createQuery(beneMatches).getResultList();
    } finally {
      benesByHashOrIdQueryNanoSeconds = timerHicnQuery.stop();

      TransformerUtils.recordQueryInMdc(
          String.format(
              "bene_by_" + hashType + ".bene_by_" + hashType + "_or_id.include_%s",
              String.join("_", includeIdentifiersValues)),
          benesByHashOrIdQueryNanoSeconds,
          matchingBenes == null ? 0 : matchingBenes.size());
    }

    // Then, if we found more than one distinct BENE_ID, or none, throw an error.
    long distinctBeneIds = matchingBenes.stream().map(b -> b.getBeneficiaryId()).distinct().count();
    Beneficiary beneficiary = null;
    if (distinctBeneIds <= 0) {
      throw new NoResultException();
    } else if (distinctBeneIds > 1) {
      throw new NonUniqueResultException();
    } else if (distinctBeneIds == 1) {
      beneficiary = matchingBenes.get(0);
    }

    // Null out the unhashed HICNs if we're not supposed to be returning them
    if (!hasHICN(includeIdentifiersValues)) {
      beneficiary.setHicnUnhashed(Optional.empty());
    }

    // Null out the unhashed MBIs if we're not supposed to be returning
    if (!hasMBI(includeIdentifiersValues)) {
      beneficiary.setMedicareBeneficiaryId(Optional.empty());
    }

    Patient patient =
        BeneficiaryTransformer.transform(metricRegistry, beneficiary, includeIdentifiersValues);
    return patient;
  }

  /**
   * Following method will bring back the Beneficiary that has the most recent rfrnc_yr since the
   * hicn points to more than one bene id in the Beneficiaries table
   *
   * @param List of matching Beneficiary records the {@link Beneficiary#getBeneficiaryId()} value to
   *     match
   * @return a FHIR {@link Beneficiary} for the CCW {@link Beneficiary} that matches the specified
   *     {@link Beneficiary#getHicn()} hash value
   */
  private Beneficiary selectBeneWithLatestReferenceYear(List<Beneficiary> duplicateBenes) {
    BigDecimal maxReferenceYear = new BigDecimal(-0001);
    String maxReferenceYearMatchingBeneficiaryId = null;

    // loop through matching bene ids looking for max rfrnc_yr
    for (Beneficiary duplicateBene : duplicateBenes) {
      // bene record found but reference year is null - still process
      if (!duplicateBene.getBeneEnrollmentReferenceYear().isPresent()) {
        duplicateBene.setBeneEnrollmentReferenceYear(Optional.of(new BigDecimal(0)));
      }
      // bene reference year is > than prior reference year
      if (duplicateBene.getBeneEnrollmentReferenceYear().get().compareTo(maxReferenceYear) > 0) {
        maxReferenceYear = duplicateBene.getBeneEnrollmentReferenceYear().get();
        maxReferenceYearMatchingBeneficiaryId = duplicateBene.getBeneficiaryId();
      }
    }

    return entityManager.find(Beneficiary.class, maxReferenceYearMatchingBeneficiaryId);
  }

  /**
   * The header key used to determine which header should be used. See {@link
   * #returnIncludeIdentifiersValues(RequestDetails)} for details.
   */
  public static final String HEADER_NAME_INCLUDE_IDENTIFIERS = "IncludeIdentifiers";

  /**
   * The List of valid values for the {@link #HEADER_NAME_INCLUDE_IDENTIFIERS} header. See {@link
   * #returnIncludeIdentifiersValues(RequestDetails)} for details.
   */
  public static final List<String> VALID_HEADER_VALUES_INCLUDE_IDENTIFIERS =
      Arrays.asList("true", "false", "hicn", "mbi");

  /**
   * Return a valid List of values for the IncludeIdenfifiers header
   *
   * @param requestDetails a {@link RequestDetails} containing the details of the request URL, used
   *     to parse out include identifiers values
   * @return List of validated header values against the {@link
   *     VALID_HEADER_VALUES_INCLUDE_IDENTIFIERS} list.
   */
  public static List<String> returnIncludeIdentifiersValues(RequestDetails requestDetails) {
    String headerValues = requestDetails.getHeader(HEADER_NAME_INCLUDE_IDENTIFIERS);

    if (headerValues == null || headerValues == "") return Arrays.asList("");
    else
      // Return values split on a comma with any whitespace, valid, distict, and sort
      return Arrays.asList(headerValues.toLowerCase().split("\\s*,\\s*")).stream()
          .peek(
              c -> {
                if (!VALID_HEADER_VALUES_INCLUDE_IDENTIFIERS.contains(c))
                  throw new InvalidRequestException(
                      "Unsupported " + HEADER_NAME_INCLUDE_IDENTIFIERS + " header value: " + c);
              })
          .distinct()
          .sorted()
          .collect(Collectors.toList());
  }

  /**
   * Check if HICN is in {@link #HEADER_NAME_INCLUDE_IDENTIFIERS} header values.
   *
   * @param includeIdentifiersValues a list of header values.
   * @return Returns true if includes unhashed hicn
   */
  public static boolean hasHICN(List<String> includeIdentifiersValues) {
    return includeIdentifiersValues.contains("hicn") || includeIdentifiersValues.contains("true");
  }

  /**
   * Check if MBI is in {@link #HEADER_NAME_INCLUDE_IDENTIFIERS} header values.
   *
   * @param includeIdentifiersValues a list of header values.
   * @return Returns true if includes unhashed mbi
   */
  public static boolean hasMBI(List<String> includeIdentifiersValues) {
    return includeIdentifiersValues.contains("mbi") || includeIdentifiersValues.contains("true");
  }
}
