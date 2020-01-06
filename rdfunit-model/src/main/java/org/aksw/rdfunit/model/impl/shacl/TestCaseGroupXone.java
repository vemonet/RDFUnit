package org.aksw.rdfunit.model.impl.shacl;

import com.google.common.collect.ImmutableSet;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import org.aksw.rdfunit.enums.RLOGLevel;
import org.aksw.rdfunit.enums.TestAppliesTo;
import org.aksw.rdfunit.enums.TestGenerationType;
import org.aksw.rdfunit.model.impl.results.ShaclTestCaseGroupResult;
import org.aksw.rdfunit.model.interfaces.TestCaseAnnotation;
import org.aksw.rdfunit.model.interfaces.TestCaseGroup;
import org.aksw.rdfunit.model.interfaces.results.TestCaseResult;
import org.aksw.rdfunit.model.interfaces.shacl.PrefixDeclaration;
import org.aksw.rdfunit.model.interfaces.shacl.ShapeTarget;
import org.aksw.rdfunit.model.interfaces.shacl.TargetBasedTestCase;
import org.aksw.rdfunit.utils.JenaUtils;
import org.aksw.rdfunit.vocabulary.SHACL;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implements the logical constraint sh:xone
 */
@EqualsAndHashCode(exclude = {"resource"})
public class TestCaseGroupXone implements TestCaseGroup {

    private final Resource resource;
    private final ShapeTarget target;
    private final ImmutableSet<TargetBasedTestCase> testCases;

    public TestCaseGroupXone(@NonNull Set<? extends TargetBasedTestCase> testCases) {
        assert(! testCases.isEmpty());
        target = testCases.iterator().next().getTarget();
        assert(testCases.stream().map(TargetBasedTestCase::getTarget).noneMatch(x -> x != target));
        this.resource = ResourceFactory.createProperty(JenaUtils.getUniqueIri());
        this.testCases = ImmutableSet.copyOf(Stream.concat(testCases.stream(), Stream.of(new AlwaysFailingTestCase(this.target))).collect(Collectors.toSet()));     // adding always failing test
    }

    @Override
    public Set<TargetBasedTestCase> getTestCases() {
        return this.testCases;
    }

    @Override
    public SHACL.LogicalConstraint getLogicalOperator() {
        return SHACL.LogicalConstraint.xone;
    }

    @Override
    public Collection<TestCaseResult> evaluateInternalResults(Collection<TestCaseResult> internalResults) {
        ImmutableSet.Builder<TestCaseResult> res = ImmutableSet.builder();
        TestCaseGroup.groupInternalResults(internalResults).forEach((focusNode, valueMap) -> {
            valueMap.forEach((value, results) ->{
                if(testCases.size() - results.size() != 1) {    // expecting exactly one correct test
                    results.forEach(r -> {
                        if(! r.getTestCaseUri().toString().startsWith(AlwaysFailingTestCase.AlwaysFailingTestCasePrefix))
                            res.add(r);
                    });
                    res.add(new ShaclTestCaseGroupResult(
                            this.resource,
                            this.getLogLevel(),
                            "More than one or all test case failed inside a sh:xone constraint.",
                            focusNode,
                            results));
                }
                //else we ignore all internal errors, since exactly one was successful
            });
        });
        return res.build();
    }

    @Override
    public TestCaseAnnotation getTestCaseAnnotation() {
        return new TestCaseAnnotation(
                this.resource,
                TestGenerationType.AutoGenerated,
                null,
                TestAppliesTo.Dataset, // TODO check
                SHACL.namespace,      // TODO check
                ImmutableSet.of(),
                "Specifies a list of shapes so that the value nodes must conform to exactly one of the shapes.",
                RLOGLevel.ERROR,    //TODO
                ImmutableSet.of()   //TODO do I have to add annotations by default?
        );
    }

    @Override
    public Collection<PrefixDeclaration> getPrefixDeclarations() {
        return testCases.stream().flatMap(t -> t.getPrefixDeclarations().stream()).collect(Collectors.toSet());
    }

    @Override
    public Resource getElement() {
        return this.resource;
    }

    @Override
    public ShapeTarget getTarget() {
        return target;
    }
}
