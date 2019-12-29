package org.aksw.rdfunit.model.interfaces;

import com.google.common.collect.ImmutableSet;
import org.aksw.rdfunit.enums.RLOGLevel;
import org.aksw.rdfunit.enums.TestAppliesTo;
import org.aksw.rdfunit.enums.TestGenerationType;
import org.aksw.rdfunit.model.helper.PropertyValuePair;
import org.aksw.rdfunit.model.helper.PropertyValuePairSet;
import org.aksw.rdfunit.model.impl.shacl.TestCaseGroupValidator;
import org.aksw.rdfunit.model.interfaces.results.ShaclLiteTestCaseResult;
import org.aksw.rdfunit.model.interfaces.results.ShaclTestCaseResult;
import org.aksw.rdfunit.model.interfaces.results.TestCaseResult;
import org.aksw.rdfunit.model.interfaces.shacl.Shape;
import org.aksw.rdfunit.model.interfaces.shacl.TargetBasedTestCase;
import org.aksw.rdfunit.utils.CommonNames;
import org.aksw.rdfunit.vocabulary.SHACL;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A wrapper object for a collection of test cases with a logical operator,
 * defining the logical relation between their results
 */
public interface TestCaseGroup extends TargetBasedTestCase {

    /**
     * The test cases which have a logical relation
     */
    List<TargetBasedTestCase> getTestCases();

    /**
     * the logical operator (default is 'atomic' which means that there is no logical relation between them)
     */
    SHACL.LogicalConstraint getLogicalOperator();

    /**
     * Function for evaluating the results of the contained tests.
     * Will return an empty set of results if the logical condition holds.
     * Will add a summary TestCaseResult if it fails.
     * @param internalResults - the result collection of the internal tests
     */
    Collection<TestCaseResult> evaluateInternalResults(Collection<TestCaseResult> internalResults);

    /**
     * The SHCAL Shape containing this constraint
     */
    Shape getPertainingShape();

    /**
     * Helper function to create a double grouping of internal test results (for a given test case group) by focus node and triple value.
     * The resulting groups should cover all results of the internal test cases of a test group for a single triple (value)
     * Example: if there are multiple instances of a given property in a focus node with invalid values,
     * without the grouping by value we could not distinguish which TestCaseResult referred to which property instance.
     * TestResults without the value annotation (e.g. results for node shapes) are grouped under the focus node iri.
     * NOTE: we assume that a given test case covers a single property path, thereby ignoring the dimension of a property path
     * @param internalResults - all TestResults stemming from all internal tests of a TestGroup
     */
    static Map<RDFNode, Map<RDFNode, List<TestCaseResult>>> groupInternalResults(Collection<TestCaseResult> internalResults){
        return internalResults.stream()
                .filter(r -> ShaclLiteTestCaseResult.class.isAssignableFrom(r.getClass()))
                .map(r -> ((ShaclLiteTestCaseResult) r))
                .collect(Collectors.groupingBy(ShaclLiteTestCaseResult::getFailingNode,
                        Collectors.groupingBy(TestCaseGroup::getValue, Collectors.<TestCaseResult>toList())));
    }

    static RDFNode getValue(ShaclLiteTestCaseResult result){
        if(ShaclTestCaseResult.class.isAssignableFrom(result.getClass())){
            Set<PropertyValuePair> values = ((ShaclTestCaseResult) result).getResultAnnotations().stream()
                    .filter(ra -> ra.getProperty().equals(SHACL.value))
                    .collect(Collectors.toSet());
            if(! values.isEmpty()){
                return values.iterator().next().getValues().iterator().next();
            }
        }
        return result.getFailingNode(); // the default case concerns non property nodes which are grouped under the focus node iri
    }

    /**
     * Will create the default TestCaseAnnotation for a TestCaseGroup
     * @param resource - the element iri
     * @param shape - the pertaining shape
     */
    static TestCaseAnnotation getTestCaseAnnotation(Resource resource, Shape shape, SHACL.LogicalConstraint operator){
        return new TestCaseAnnotation(
                resource,
                TestGenerationType.AutoGenerated,
                null,
                TestAppliesTo.Dataset, // TODO check
                SHACL.namespace,      // TODO check
                ImmutableSet.of(),
                "A constraint component that can be used to test whether a value node conforms to all members of a provided list of shapes.",
                Optional.ofNullable(RLOGLevel.resolve(shape.getSeverity().getURI())).orElse(RLOGLevel.ERROR), //FIXME defaults to ERROR, correct? @Dimitris
                TestCaseGroupValidator.getGroupResultAnnotations(shape, operator)
        );
    }

    /**
     * TODO
     * @param annotations
     * @param focusNode
     * @return
     */
    static PropertyValuePairSet convertResultAnnotations(TestCaseAnnotation annotations, RDFNode focusNode){
        PropertyValuePairSet.PropertyValuePairSetBuilder annotationSetBuilder = PropertyValuePairSet.builder();
        // get static annotations for new test
        for (ResultAnnotation resultAnnotation : annotations.getResultAnnotations()) {
            PropertyValuePair.fromAnnotation(resultAnnotation).ifPresent(annotationSetBuilder::annotation);
        }
        // get annotations from the SPARQL query
        for (ResultAnnotation resultAnnotation : annotations.getVariableAnnotations()) {
            // Get the variable name
            if (resultAnnotation.getAnnotationVarName().isPresent()) {
                String variable = resultAnnotation.getAnnotationVarName().get().trim();
                //TODO we only allow a subset of properties for use in TestCaseGroups since we do not have a query -> please check if this makes sense @Dimitris
                if(Arrays.asList(SHACL.value, SHACL.focusNode).contains(resultAnnotation.getAnnotationProperty())) {
                    if (variable.equals(CommonNames.This)) {    // this should always be the case for TestCaseGroup
                        annotationSetBuilder.annotation(
                                PropertyValuePair.create(resultAnnotation.getAnnotationProperty(), focusNode));
                    }
                }
            }
        }
        return annotationSetBuilder.build();
    }
}
