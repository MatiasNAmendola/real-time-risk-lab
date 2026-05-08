package io.riskplatform.atdd;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.*;

/**
 * JUnit 5 Platform Suite entry point for Cucumber.
 * <p>
 * Run all features: {@code ./gradlew :tests:risk-engine-atdd:test}
 * Run by tag:       {@code ./gradlew :tests:risk-engine-atdd:test -Dcucumber.filter.tags=@idempotency}
 * Skip @wip:        {@code ./gradlew :tests:risk-engine-atdd:test -Dcucumber.filter.tags="not @wip"}
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "io.riskplatform.atdd")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME,
        value = "pretty,html:build/cucumber-reports/report.html,json:build/cucumber-reports/report.json")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "not @wip and not @karate-only")
public class RunCucumberTest {
    // Suite runner — no code needed here.
}
