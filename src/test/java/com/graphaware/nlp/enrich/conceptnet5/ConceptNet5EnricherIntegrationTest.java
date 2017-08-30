package com.graphaware.nlp.enrich.conceptnet5;

import com.graphaware.nlp.NLPIntegrationTest;
import com.graphaware.nlp.configuration.DynamicConfiguration;
import com.graphaware.nlp.dsl.ConceptRequest;
import com.graphaware.nlp.persistence.PersistenceRegistry;
import com.graphaware.nlp.processor.TextProcessorsManager;
import com.graphaware.nlp.stub.StubTextProcessor;
import com.graphaware.nlp.util.TestNLPGraph;
import org.junit.Test;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;

import java.util.Collections;

import static org.junit.Assert.*;

public class ConceptNet5EnricherIntegrationTest extends NLPIntegrationTest {

    @Override
    public void setUp() throws Exception {
        super.setUp();
        createTagConstraint();
    }

    @Test
    public void testConceptNetUrlIsConfigurable() {
        DynamicConfiguration configuration = new DynamicConfiguration(getDatabase());
        PersistenceRegistry registry = new PersistenceRegistry(getDatabase(), configuration);
        ConceptNet5Enricher enricher = new ConceptNet5Enricher(getDatabase(), registry, configuration, new TextProcessorsManager(getDatabase()));
        assertEquals("http://api.conceptnet.io", enricher.getConceptNetUrl());

        configuration.updateInternalSetting("CONCEPT_NET_5_URL", "http://localhost:8001");
        assertEquals("http://localhost:8001", enricher.getConceptNetUrl());
    }

    @Test
    public void testTagsCanBeEnrichedWithConceptNet5() {
        DynamicConfiguration configuration = new DynamicConfiguration(getDatabase());
        PersistenceRegistry registry = new PersistenceRegistry(getDatabase(), configuration);
        ConceptNet5Enricher enricher = new ConceptNet5Enricher(getDatabase(), registry, configuration, new TextProcessorsManager(getDatabase()));

        clearDb();
        executeInTransaction("CALL ga.nlp.annotate({text: 'kill cats', id: 'test-proc', checkLanguage: false})", emptyConsumer());

        try (Transaction tx = getDatabase().beginTx()) {
            getDatabase().findNodes(Label.label("AnnotatedText")).stream().forEach(node -> {
                ConceptRequest request = new ConceptRequest();
                request.setAnnotatedNode(node);
                request.setLanguage("en");
                request.setDepth(1);
                request.setProcessor(StubTextProcessor.class.getName());
                request.setAdmittedRelationships(Collections.singletonList("RelatedTo"));
                request.setFilterByLanguage(true);
                request.setSplitTag(false);

                enricher.importConcept(request);

                tx.success();
            });
        }

        debugTagsRelations();

        TestNLPGraph tester = new TestNLPGraph(getDatabase());
        tester.assertTagWithValueExist("cat");
        tester.assertTagHasRelatedTag("cats", "cat");
        tester.assertTagHasRelatedTag("kill", "death");
    }

    private void createTagConstraint() {
        executeInTransaction("CREATE CONSTRAINT ON (t:Tag) ASSERT t.id IS UNIQUE;", (result -> {
        }));
    }

    private void debugTagsRelations() {
        try (Transaction tx = getDatabase().beginTx()) {
            getDatabase().findNodes(Label.label("Tag")).forEachRemaining(node -> {
                node.getRelationships(RelationshipType.withName("IS_RELATED_TO")).forEach(relationship -> {
                    System.out.println(node.getAllProperties());
                    System.out.println(relationship.getOtherNode(node).getAllProperties());
                });

            });

            tx.success();
        }
    }

}