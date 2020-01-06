package org.aksw.rdfunit.model.impl.shacl;

import com.google.common.collect.ImmutableList;
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

/**
 * Implements the logical constraint sh:or
 */
@EqualsAndHashCode(exclude = {"resource"})
public class TestCaseGroupOr implements TestCaseGroup {

    private final ShapeTarget target;
    private final Resource resource;
    private final ImmutableSet<TargetBasedTestCase> testCases;

    public TestCaseGroupOr(@NonNull Set<? extends TargetBasedTestCase> testCases) {
        assert(! testCases.isEmpty());
        target = testCases.iterator().next().getTarget();
        assert(testCases.stream().map(TargetBasedTestCase::getTarget).noneMatch(x -> x != target));
        this.resource = ResourceFactory.createProperty(JenaUtils.getUniqueIri());
        this.testCases = ImmutableSet.copyOf(testCases);
    }

    @Override
    public Set<TargetBasedTestCase> getTestCases() {
        return this.testCases;
    }

    @Override
    public SHACL.LogicalConstraint getLogicalOperator() {
        return SHACL.LogicalConstraint.or;
    }

    @Override
    public Collection<TestCaseResult> evaluateInternalResults(Collection<TestCaseResult> internalResults) {
        ImmutableList.Builder<TestCaseResult> res = ImmutableList.builder();
        TestCaseGroup.groupInternalResults(internalResults).forEach((focusNode, valueMap) -> {
            valueMap.forEach((value, results) ->{
                if(results.size() == this.testCases.size()) {
                    res.addAll(results);
                    res.add(new ShaclTestCaseGroupResult(
                            this.resource,
                            this.getLogLevel(),
                            "All test case failed inside a sh:or constraint.",
                            focusNode,
                            results));
                }
                //else we ignore all internal errors, since at least one was successful
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
                "Specifies a list of shapes so that the value nodes must conform to at least one of the shapes.",
                RLOGLevel.ERROR,
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
