package com.edualoca.bdd;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * Executa src/test/resources/features/aprovacao-bloqueio.feature na JUnit Platform.
 * O sufixo "Test" garante que o Surefire roda a feature em {@code ./mvnw -B verify}.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/aprovacao-bloqueio.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.edualoca.bdd")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty, summary")
public class AprovacaoBloqueioFeatureTest {
}
