package gov.cms.bfd.pipeline.rif.load;

import com.codahale.metrics.MetricRegistry;
import com.justdavis.karl.misc.exceptions.BadCodeMonkeyException;
import gov.cms.bfd.model.rif.Beneficiary;
import gov.cms.bfd.model.rif.LoadedFile;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

/**
 * Contains utilities that are useful when running the {@link RifLoader}.
 *
 * <p>This is being left in <code>src/main</code> so that it can be used from other modules' tests,
 * without having to delve into classpath dark arts.
 */
public final class RifLoaderTestUtils {
  /** The value to use for {@link LoadAppOptions#getHicnHashIterations()} in tests. */
  public static final int HICN_HASH_ITERATIONS = 2;

  /** The value to use for {@link LoadAppOptions#getHicnHashPepper()} in tests. */
  public static final byte[] HICN_HASH_PEPPER = "nottherealpepper".getBytes(StandardCharsets.UTF_8);

  /** The value to use for {@link LoadAppOptions#isIdempotencyRequired()}. */
  public static final boolean IDEMPOTENCY_REQUIRED = true;

  /** The value to use for {@link LoadAppOptions#isFixupsEnabled()} */
  public static final boolean FIXUPS_ENABLED = false;
  
  private static final Logger LOGGER = LoggerFactory.getLogger(RifLoaderTestUtils.class);

  /**
   * A wrapper for the entity manager logic
   *
   * @param consumer to call with an entity manager
   */
  public static void doTestDb(Consumer<EntityManager> consumer) {
    LoadAppOptions options = RifLoaderTestUtils.getLoadOptions();
    EntityManagerFactory entityManagerFactory =
        RifLoaderTestUtils.createEntityManagerFactory(options);
    EntityManager entityManager = null;
    try {
      entityManager = entityManagerFactory.createEntityManager();
      consumer.accept(entityManager);
    } finally {
      if (entityManager != null) entityManager.close();
    }
  }

  /**
   * <strong>Serious Business:</strong> deletes all resources from the database server used in
   * tests.
   *
   * @param options the {@link LoadAppOptions} specifying the DB to clean
   */
  @SuppressWarnings({"rawtypes", "unchecked"})
  public static void cleanDatabaseServerViaDeletes(LoadAppOptions options) {
    // Before disabling this check, please go and update your resume.
    if (!DB_URL.contains("hsql"))
      throw new BadCodeMonkeyException("Saving you from a career-changing event.");

    EntityManagerFactory entityManagerFactory = createEntityManagerFactory(options);
    EntityManager entityManager = null;
    EntityTransaction transaction = null;
    try {
      entityManager = entityManagerFactory.createEntityManager();

      // Determine the entity types to delete, and the order to do so in.
      Comparator<Class<?>> entityDeletionSorter =
          (t1, t2) -> {
            if (t1.equals(Beneficiary.class)) return 1;
            if (t2.equals(Beneficiary.class)) return -1;
            if (t1.getSimpleName().endsWith("Line")) return -1;
            if (t2.getSimpleName().endsWith("Line")) return 1;
            if (t1.equals(LoadedFile.class)) return 1;
            if (t2.equals(LoadedFile.class)) return -1;
            return 0;
          };
      List<Class<?>> entityTypesInDeletionOrder =
          entityManagerFactory.getMetamodel().getEntities().stream()
              .map(t -> t.getJavaType())
              .sorted(entityDeletionSorter)
              .collect(Collectors.toList());

      LOGGER.info("Deleting all resources...");
      transaction = entityManager.getTransaction();
      transaction.begin();
      for (Class<?> entityClass : entityTypesInDeletionOrder) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaDelete query = builder.createCriteriaDelete(entityClass);
        query.from(entityClass);
        entityManager.createQuery(query).executeUpdate();
      }
      transaction.commit();
      LOGGER.info("Deleted all resources.");
    } finally {
      if (transaction != null && transaction.isActive()) transaction.rollback();
      if (entityManager != null) entityManager.close();
    }
  }

  /**
   * <strong>Serious Business:</strong> deletes all resources from the database server used in
   * tests.
   *
   * @param options the {@link LoadAppOptions} specifying the DB to clean
   */
  public static void cleanDatabaseServer(LoadAppOptions options) {
    // Before disabling this check, please go and update your resume.
    if (!options.getDatabaseUrl().contains("hsql"))
      throw new BadCodeMonkeyException("Saving you from a career-changing event.");

    Flyway flyway = new Flyway();
    flyway.setDataSource(RifLoader.createDataSource(options, new MetricRegistry()));
    flyway.clean();
  }

  /**
   * @param dataSource a {@link DataSource} for the test DB to connect to
   * @return the {@link LoadAppOptions} that should be used in tests, which specifies how to connect
   *     to the database server that tests should be run against
   */
  public static LoadAppOptions getLoadOptions(DataSource dataSource) {
    return new LoadAppOptions(
        HICN_HASH_ITERATIONS,
        HICN_HASH_PEPPER,
        dataSource,
        LoadAppOptions.DEFAULT_LOADER_THREADS,
        IDEMPOTENCY_REQUIRED,
        FIXUPS_ENABLED,
        RifLoaderIdleTasks.DEFAULT_PARTITION_COUNT);
  }

  /**
   * @param options the {@link LoadAppOptions} specifying the DB to use
   * @return a JPA {@link EntityManagerFactory} for the database server used in tests
   */
  public static EntityManagerFactory createEntityManagerFactory(LoadAppOptions options) {
    if (options.getDatabaseDataSource() == null) {
      throw new IllegalStateException("DB DataSource (not URLs) must be used in tests.");
    }

    DataSource dataSource = options.getDatabaseDataSource();
    return RifLoader.createEntityManagerFactory(dataSource);
  }
}
