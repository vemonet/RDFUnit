package org.aksw.rdfunit.model.impl.shacl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import lombok.*;
import org.aksw.rdfunit.enums.ComponentValidatorType;
import org.aksw.rdfunit.enums.RLOGLevel;
import org.aksw.rdfunit.enums.TestAppliesTo;
import org.aksw.rdfunit.enums.TestGenerationType;
import org.aksw.rdfunit.model.helper.RdfListUtils;
import org.aksw.rdfunit.model.impl.ManualTestCaseImpl;
import org.aksw.rdfunit.model.impl.ResultAnnotationImpl;
import org.aksw.rdfunit.model.interfaces.ResultAnnotation;
import org.aksw.rdfunit.model.interfaces.TestCase;
import org.aksw.rdfunit.model.interfaces.TestCaseAnnotation;
import org.aksw.rdfunit.model.interfaces.shacl.*;
import org.aksw.rdfunit.utils.JenaUtils;
import org.aksw.rdfunit.vocabulary.SHACL;
import org.apache.jena.rdf.model.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

@Builder
@Value
public class ConstraintImpl implements Constraint {
    @Getter @NonNull private final Shape shape;
    @Getter @NonNull private final String message;
    @Getter @NonNull private final RLOGLevel severity;
    @Getter @NonNull private final Component component;
    @NonNull private final ComponentValidator validator;
    @Getter @NonNull @Singular private final ImmutableMap<ComponentParameter, RDFNode> bindings;

    @Override
    public TestCase getTestCase() {

        ManualTestCaseImpl.ManualTestCaseImplBuilder testBuilder = ManualTestCaseImpl.builder();
        String sparql;
        sparql = generateSparqlWhere(validator.getSparqlQuery());


        return testBuilder
                .element(createTestCaseResource())
                .sparqlPrevalence("")
                .sparqlWhere(sparql)
                .prefixDeclarations(validator.getPrefixDeclarations())
                .testCaseAnnotation(generateTestAnnotations())
                .build();
    }
    private String generateSparqlWhere(String sparqlString) {

        String valuePath;
        if (shape.getPath().isPresent()) {
            valuePath = " ?this " + shape.getPath().get().asSparqlPropertyPath() + " ?value . ";
        } else {
            valuePath = " BIND ($this AS ?value) . ";
        }

        if (validator.getType().equals(ComponentValidatorType.ASK_VALIDATOR)) {
            String sparqlWhere = sparqlString.trim()
                    .replaceFirst("\\{", "")
                    .replaceFirst("ASK", Matcher.quoteReplacement("  {\n " + valuePath + "\n MINUS {\n " + valuePath + " "))
                    + "}";
            return replaceBindings(sparqlWhere);
        } else {
            String  sparqlWhere = sparqlString
                    .substring(sparqlString.indexOf('{'));
            if (shape.getPath().isPresent()) {
                    sparqlWhere = sparqlWhere.replace("$PATH", shape.getPath().get().asSparqlPropertyPath());
            }
            return replaceBindings(sparqlWhere);
        }
    }

    private String replaceBindings(String sparqlSnippet) {
        String bindedSnippet = sparqlSnippet;
        for (Map.Entry<ComponentParameter, RDFNode>  entry:  bindings.entrySet()) {
            bindedSnippet = replaceBinding(bindedSnippet, entry.getKey(), entry.getValue());
        }
        if (shape.isPropertyShape()) {
            bindedSnippet = bindedSnippet.replace("$PATH", shape.getPath().get().asSparqlPropertyPath());
        }
        return bindedSnippet;
    }

    private String replaceBinding(String sparql, ComponentParameter componentParameter, RDFNode value) {
        return sparql.replace("$"+ componentParameter.getPredicate().getLocalName(), formatRdfValue(value));
    }

    private String generateMessage() {
        return replaceBindings(this.message);
    }

    private String formatRdfValue(RDFNode value) {
        if (value.isResource()) {
            Resource r = value.asResource();
            if (RdfListUtils.isList(r)) {
                return RdfListUtils.getListItemsOrEmpty(r).stream().map(this::formatRdfListValue).collect(Collectors.joining(" , "));
            } else {
                return asFullTurtleUri(r);
            }

        } else {
            return asSimpleLiteral(value.asLiteral());
        }
    }

    private String asSimpleLiteral(Literal value) {
        return value.getLexicalForm();
    }

    private String asFullTurtleLiteral(Literal value) {
        return "\""+value.getLexicalForm()+"\"^^<"+value.getDatatypeURI()+">";
    }

    private String asFullTurtleUri(Resource value) {
        // some vocabularies use spaces in uris
        return "<" + value.getURI().trim().replace(" ", "") + ">";
    }

    private String formatRdfListValue(RDFNode listVal) {
        if (listVal.isResource()) {
            return asFullTurtleUri(listVal.asResource());
        } else {
            return asFullTurtleLiteral(listVal.asLiteral());
        }
    }

    // hack for now
    private TestCaseAnnotation generateTestAnnotations() {
        return new TestCaseAnnotation(
                createTestCaseResource(),
                TestGenerationType.AutoGenerated,
                null,
                TestAppliesTo.Schema, // TODO check
                SHACL.namespace,      // TODO check
                Collections.emptyList(),
                generateMessage(),
                RLOGLevel.ERROR, //FIXME
                createResultAnnotations()
        );
    }

    private List<ResultAnnotation> createResultAnnotations() {
        ImmutableList.Builder<ResultAnnotation> annotations = ImmutableList.builder();
        // add property
        if (shape.getPath().isPresent()) {
            annotations.add(new ResultAnnotationImpl.Builder(ResourceFactory.createResource(), SHACL.resultPath)
                    .setValue(shape.getPath().get().getElement()).build());
        }

        annotations.add(new ResultAnnotationImpl.Builder(ResourceFactory.createResource(), SHACL.sourceShape)
                    .setValue(shape.getElement()).build());
        annotations.add(new ResultAnnotationImpl.Builder(ResourceFactory.createResource(), SHACL.sourceConstraintComponent)
                .setValue(component.getElement()).build());


        List<Property> nonValueArgs = Arrays.asList(SHACL.minCount, SHACL.maxCount);
        List<Property> nonValueBind = getBindings().keySet().stream()
                .map(ComponentParameter::getPredicate)
                .filter( p -> !nonValueArgs.contains(p))
                .collect(Collectors.toList());
        if (!nonValueBind.isEmpty()) {
            annotations.add(new ResultAnnotationImpl.Builder(ResourceFactory.createResource(), SHACL.value)
                    .setVariableName("value").build());
        }

        return annotations.build();
    }

    private Resource createTestCaseResource() {
        // FIXME temporary solution until we decide how to build stable unique test uris
        return ResourceFactory.createProperty(JenaUtils.getUniqueIri());
    }
}
