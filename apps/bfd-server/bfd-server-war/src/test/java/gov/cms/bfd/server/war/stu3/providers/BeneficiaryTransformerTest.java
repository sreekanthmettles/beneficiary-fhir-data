package gov.cms.bfd.server.war.stu3.providers;

import com.codahale.metrics.MetricRegistry;
import gov.cms.bfd.model.codebook.data.CcwCodebookVariable;
import gov.cms.bfd.model.rif.Beneficiary;
import gov.cms.bfd.model.rif.BeneficiaryHistory;
import gov.cms.bfd.model.rif.MedicareBeneficiaryIdHistory;
import gov.cms.bfd.model.rif.samples.StaticRifResource;
import gov.cms.bfd.model.rif.samples.StaticRifResourceGroup;
import gov.cms.bfd.server.war.ServerTestUtils;
import java.sql.Date;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.hl7.fhir.dstu3.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.dstu3.model.Identifier;
import org.hl7.fhir.dstu3.model.Patient;
import org.junit.Assert;
import org.junit.Test;

/** Unit tests for {@link gov.cms.bfd.server.war.stu3.providers.BeneficiaryTransformer}. */
public final class BeneficiaryTransformerTest {
  /**
   * Verifies that {@link
   * gov.cms.bfd.server.war.stu3.providers.BeneficiaryTransformer#transform(Beneficiary)} works as
   * expected when run against the {@link StaticRifResource#SAMPLE_A_BENES} {@link Beneficiary}.
   */
  @Test
  public void transformSampleARecord() {
    Beneficiary beneficiary = loadSampleABeneficiary();

    Patient patient =
        BeneficiaryTransformer.transform(new MetricRegistry(), beneficiary, Arrays.asList("false"));
    assertMatches(beneficiary, patient);

    Assert.assertEquals("Number of identifiers should be 2", 2, patient.getIdentifier().size());

    // Verify identifiers and values match.
    assertValuesInPatientIdentifiers(
        patient,
        TransformerUtils.calculateVariableReferenceUrl(CcwCodebookVariable.BENE_ID),
        "567834");
    assertValuesInPatientIdentifiers(
        patient, TransformerConstants.CODING_BBAPI_BENE_HICN_HASH, "somehash");
  }

  /**
   * Verifies that {@link
   * gov.cms.bfd.server.war.stu3.providers.BeneficiaryTransformer#transform(Beneficiary)} works as
   * expected when run against the {@link StaticRifResource#SAMPLE_A_BENES} {@link Beneficiary},
   * with {@link IncludeIdentifiersValues} = ["hicn","mbi"].
   */
  @Test
  public void transformSampleARecordWithIdentifiers() {
    Beneficiary beneficiary = loadSampleABeneficiary();

    Patient patient =
        BeneficiaryTransformer.transform(
            new MetricRegistry(), beneficiary, Arrays.asList("hicn", "mbi"));
    assertMatches(beneficiary, patient);

    Assert.assertEquals("Number of identifiers should be 7", 7, patient.getIdentifier().size());

    // Verify patient identifiers and values match.
    assertValuesInPatientIdentifiers(
        patient,
        TransformerUtils.calculateVariableReferenceUrl(CcwCodebookVariable.BENE_ID),
        "567834");
    assertValuesInPatientIdentifiers(
        patient, TransformerConstants.CODING_BBAPI_BENE_HICN_HASH, "somehash");
    assertValuesInPatientIdentifiers(
        patient, TransformerConstants.CODING_BBAPI_BENE_HICN_UNHASHED, "543217066U");
    assertValuesInPatientIdentifiers(
        patient, TransformerConstants.CODING_BBAPI_MEDICARE_BENEFICIARY_ID_UNHASHED, "3456789");
    assertValuesInPatientIdentifiers(
        patient, TransformerConstants.CODING_BBAPI_BENE_HICN_UNHASHED, "543217066T");
    assertValuesInPatientIdentifiers(
        patient, TransformerConstants.CODING_BBAPI_BENE_HICN_UNHASHED, "543217066Z");
    assertValuesInPatientIdentifiers(
        patient, TransformerConstants.CODING_BBAPI_MEDICARE_BENEFICIARY_ID_UNHASHED, "9AB2WW3GR44");
  }

  /**
   * Verifies that {@link
   * gov.cms.bfd.server.war.stu3.providers.BeneficiaryTransformer#transform(Beneficiary)} works as
   * expected when run against the {@link StaticRifResource#SAMPLE_A_BENES} {@link Beneficiary},
   * with {@link IncludeIdentifiersValues} = ["true"].
   */
  @Test
  public void transformSampleARecordWithIdentifiersTrue() {
    Beneficiary beneficiary = loadSampleABeneficiary();

    Patient patient =
        BeneficiaryTransformer.transform(new MetricRegistry(), beneficiary, Arrays.asList("true"));
    assertMatches(beneficiary, patient);

    Assert.assertEquals("Number of identifiers should be 7", 7, patient.getIdentifier().size());

    // Verify patient identifiers and values match.
    assertValuesInPatientIdentifiers(
        patient,
        TransformerUtils.calculateVariableReferenceUrl(CcwCodebookVariable.BENE_ID),
        "567834");
    assertValuesInPatientIdentifiers(
        patient, TransformerConstants.CODING_BBAPI_BENE_HICN_HASH, "somehash");
    assertValuesInPatientIdentifiers(
        patient, TransformerConstants.CODING_BBAPI_BENE_HICN_UNHASHED, "543217066U");
    assertValuesInPatientIdentifiers(
        patient, TransformerConstants.CODING_BBAPI_MEDICARE_BENEFICIARY_ID_UNHASHED, "3456789");
    assertValuesInPatientIdentifiers(
        patient, TransformerConstants.CODING_BBAPI_BENE_HICN_UNHASHED, "543217066T");
    assertValuesInPatientIdentifiers(
        patient, TransformerConstants.CODING_BBAPI_BENE_HICN_UNHASHED, "543217066Z");
    assertValuesInPatientIdentifiers(
        patient, TransformerConstants.CODING_BBAPI_MEDICARE_BENEFICIARY_ID_UNHASHED, "9AB2WW3GR44");
  }

  /**
   * Verifies that {@link
   * gov.cms.bfd.server.war.stu3.providers.BeneficiaryTransformer#transform(Beneficiary)} works as
   * expected when run against the {@link StaticRifResource#SAMPLE_A_BENES} {@link Beneficiary},
   * with {@link IncludeIdentifiersValues} = ["hicn"].
   */
  @Test
  public void transformSampleARecordWithIdentifiersHicn() {
    Beneficiary beneficiary = loadSampleABeneficiary();

    Patient patient =
        BeneficiaryTransformer.transform(new MetricRegistry(), beneficiary, Arrays.asList("hicn"));
    assertMatches(beneficiary, patient);

    Assert.assertEquals("Number of identifiers should be 5", 5, patient.getIdentifier().size());

    // Verify patient identifiers and values match.
    assertValuesInPatientIdentifiers(
        patient,
        TransformerUtils.calculateVariableReferenceUrl(CcwCodebookVariable.BENE_ID),
        "567834");
    assertValuesInPatientIdentifiers(
        patient, TransformerConstants.CODING_BBAPI_BENE_HICN_HASH, "somehash");
    assertValuesInPatientIdentifiers(
        patient, TransformerConstants.CODING_BBAPI_BENE_HICN_UNHASHED, "543217066U");
    assertValuesInPatientIdentifiers(
        patient, TransformerConstants.CODING_BBAPI_BENE_HICN_UNHASHED, "543217066T");
    assertValuesInPatientIdentifiers(
        patient, TransformerConstants.CODING_BBAPI_BENE_HICN_UNHASHED, "543217066Z");
  }

  /**
   * Verifies that {@link
   * gov.cms.bfd.server.war.stu3.providers.BeneficiaryTransformer#transform(Beneficiary)} works as
   * expected when run against the {@link StaticRifResource#SAMPLE_A_BENES} {@link Beneficiary},
   * with {@link IncludeIdentifiersValues} = ["mbi"].
   */
  @Test
  public void transformSampleARecordWithIdentifiersMbi() {
    Beneficiary beneficiary = loadSampleABeneficiary();

    Patient patient =
        BeneficiaryTransformer.transform(new MetricRegistry(), beneficiary, Arrays.asList("mbi"));
    assertMatches(beneficiary, patient);

    Assert.assertEquals("Number of identifiers should be 4", 4, patient.getIdentifier().size());

    // Verify patient identifiers and values match.
    assertValuesInPatientIdentifiers(
        patient,
        TransformerUtils.calculateVariableReferenceUrl(CcwCodebookVariable.BENE_ID),
        "567834");
    assertValuesInPatientIdentifiers(
        patient, TransformerConstants.CODING_BBAPI_BENE_HICN_HASH, "somehash");
    assertValuesInPatientIdentifiers(
        patient, TransformerConstants.CODING_BBAPI_MEDICARE_BENEFICIARY_ID_UNHASHED, "3456789");
    assertValuesInPatientIdentifiers(
        patient, TransformerConstants.CODING_BBAPI_MEDICARE_BENEFICIARY_ID_UNHASHED, "9AB2WW3GR44");
  }

  /**
   * Verifies that the {@link Patient} identifiers contain expected values.
   *
   * @param Patient {@link Patient} containing identifiers
   * @param identifierSystem value to be matched
   * @param identifierValue value to be matched
   */
  private static void assertValuesInPatientIdentifiers(
      Patient patient, String identifierSystem, String identifierValue) {
    boolean identifierFound = false;

    for (Identifier temp : patient.getIdentifier()) {
      if (identifierSystem.equals(temp.getSystem()) && identifierValue.equals(temp.getValue())) {
        identifierFound = true;
        break;
      }
    }
    Assert.assertEquals(
        "Identifier "
            + identifierSystem
            + " value = "
            + identifierValue
            + " does not match an expected value.",
        identifierFound,
        true);
  }

  /**
   * @return the {@link StaticRifResourceGroup#SAMPLE_A} {@link Beneficiary} record, with the {@link
   *     Beneficiary#getBeneficiaryHistories()} and {@link
   *     Beneficiary#getMedicareBeneficiaryIdHistories()} fields populated.
   */
  private static Beneficiary loadSampleABeneficiary() {
    List<Object> parsedRecords =
        ServerTestUtils.parseData(Arrays.asList(StaticRifResourceGroup.SAMPLE_A.getResources()));

    // Pull out the base Beneficiary record and fix its HICN fields.
    Beneficiary beneficiary =
        parsedRecords.stream()
            .filter(r -> r instanceof Beneficiary)
            .map(r -> (Beneficiary) r)
            .findFirst()
            .get();
    beneficiary.setHicnUnhashed(Optional.of(beneficiary.getHicn()));
    beneficiary.setHicn("somehash");

    // Add the HICN history records to the Beneficiary, and fix their HICN fields.
    Set<BeneficiaryHistory> beneficiaryHistories =
        parsedRecords.stream()
            .filter(r -> r instanceof BeneficiaryHistory)
            .map(r -> (BeneficiaryHistory) r)
            .filter(r -> beneficiary.getBeneficiaryId().equals(r.getBeneficiaryId()))
            .collect(Collectors.toSet());
    beneficiary.getBeneficiaryHistories().addAll(beneficiaryHistories);
    for (BeneficiaryHistory beneficiaryHistory : beneficiary.getBeneficiaryHistories()) {
      beneficiaryHistory.setHicnUnhashed(Optional.of(beneficiaryHistory.getHicn()));
      beneficiaryHistory.setHicn("somehash");
    }

    // Add the MBI history records to the Beneficiary.
    Set<MedicareBeneficiaryIdHistory> beneficiaryMbis =
        parsedRecords.stream()
            .filter(r -> r instanceof MedicareBeneficiaryIdHistory)
            .map(r -> (MedicareBeneficiaryIdHistory) r)
            .filter(r -> beneficiary.getBeneficiaryId().equals(r.getBeneficiaryId().orElse(null)))
            .collect(Collectors.toSet());
    beneficiary.getMedicareBeneficiaryIdHistories().addAll(beneficiaryMbis);

    return beneficiary;
  }

  /**
   * Verifies that {@link gov.cms.bfd.server.war.stu3.providers.BeneficiaryTransformer} works
   * correctly when passed a {@link Beneficiary} where all {@link Optional} fields are set to {@link
   * Optional#empty()}.
   */
  @Test
  public void transformBeneficiaryWithAllOptionalsEmpty() {
    List<Object> parsedRecords =
        ServerTestUtils.parseData(Arrays.asList(StaticRifResourceGroup.SAMPLE_A.getResources()));
    Beneficiary beneficiary =
        parsedRecords.stream()
            .filter(r -> r instanceof Beneficiary)
            .map(r -> (Beneficiary) r)
            .findFirst()
            .get();
    TransformerTestUtils.setAllOptionalsToEmpty(beneficiary);

    Patient patient =
        BeneficiaryTransformer.transform(new MetricRegistry(), beneficiary, Arrays.asList(""));
    assertMatches(beneficiary, patient);
  }

  /**
   * Verifies that the {@link Patient} "looks like" it should, if it were produced from the
   * specified {@link Beneficiary}.
   *
   * @param beneficiary the {@link Beneficiary} that the {@link Patient} was generated from
   * @param patient the {@link Patient} that was generated from the specified {@link Beneficiary}
   */
  static void assertMatches(Beneficiary beneficiary, Patient patient) {
    TransformerTestUtils.assertNoEncodedOptionals(patient);

    Assert.assertEquals(beneficiary.getBeneficiaryId(), patient.getIdElement().getIdPart());
    Assert.assertEquals(1, patient.getAddress().size());
    Assert.assertEquals(beneficiary.getStateCode(), patient.getAddress().get(0).getState());
    Assert.assertEquals(beneficiary.getCountyCode(), patient.getAddress().get(0).getDistrict());
    Assert.assertEquals(beneficiary.getPostalCode(), patient.getAddress().get(0).getPostalCode());
    Assert.assertEquals(Date.valueOf(beneficiary.getBirthDate()), patient.getBirthDate());
    if (beneficiary.getSex() == Sex.MALE.getCode())
      Assert.assertEquals(
          AdministrativeGender.MALE.toString(), patient.getGender().toString().trim());
    else if (beneficiary.getSex() == Sex.FEMALE.getCode())
      Assert.assertEquals(
          AdministrativeGender.FEMALE.toString(), patient.getGender().toString().trim());
    TransformerTestUtils.assertExtensionCodingEquals(
        CcwCodebookVariable.RACE, beneficiary.getRace(), patient);
    Assert.assertEquals(
        beneficiary.getNameGiven(), patient.getName().get(0).getGiven().get(0).toString());
    if (beneficiary.getNameMiddleInitial().isPresent())
      Assert.assertEquals(
          beneficiary.getNameMiddleInitial().get().toString(),
          patient.getName().get(0).getGiven().get(1).toString());
    Assert.assertEquals(beneficiary.getNameSurname(), patient.getName().get(0).getFamily());

    if (beneficiary.getMedicaidDualEligibilityFebCode().isPresent())
      TransformerTestUtils.assertExtensionCodingEquals(
          CcwCodebookVariable.DUAL_02, beneficiary.getMedicaidDualEligibilityFebCode(), patient);
    if (beneficiary.getBeneEnrollmentReferenceYear().isPresent())
      TransformerTestUtils.assertExtensionDateYearEquals(
          CcwCodebookVariable.RFRNC_YR, beneficiary.getBeneEnrollmentReferenceYear(), patient);
  }
}
