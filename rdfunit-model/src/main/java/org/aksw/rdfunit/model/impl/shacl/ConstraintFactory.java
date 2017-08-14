package org.aksw.rdfunit.model.impl.shacl;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.aksw.rdfunit.model.helper.PropertySingleValuePair;
import org.aksw.rdfunit.model.interfaces.shacl.*;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public final class ConstraintFactory {

    private ConstraintFactory() {}

    public static Set<ComponentConstraint> createFromShapeAndComponent(Shape shape, Component component) {

        ImmutableSet.Builder<ComponentConstraint> constraints = ImmutableSet.builder();

        getComponentShapeBindings(component, shape).forEach( bindingPairs -> {

            // get bindings
            Map<ComponentParameter, RDFNode> bindings = new HashMap<>();
            component.getParameters().forEach(parameter -> {
                Optional<RDFNode> node = bindingPairs.stream()
                        .filter(p -> p.getProperty().equals(parameter.getPredicate()))
                        .map(PropertySingleValuePair::getValue)
                        .findAny();
                node.ifPresent(rdfNode -> bindings.put(parameter, rdfNode));
            });

            // get validators
            Set<ComponentValidator> validators =
                    component.getValidators().stream()
                            .filter(v -> v.filterAppliesForBindings(shape.getShapeType(), bindings))
                            .collect(Collectors.toSet());


            if (validators.isEmpty()) {
                log.warn("No validators found for shape {} and component {}", shape, component);
            }

            // for multiple validators
            validators.forEach(validator -> {
                ComponentConstraintImpl.ComponentConstraintImplBuilder constraintBuilder = ComponentConstraintImpl.builder();

                constraintBuilder
                        .shape(shape)
                        .component(component)
                        .bindings(bindings)
                        .validator(validator)
                ;

                constraints.add(constraintBuilder.build());
            });

        });

        return constraints.build();
    }

    private static Set<Set<PropertySingleValuePair>> getComponentShapeBindings(Component component, Shape shape) {

        Set<Property> parameterProperties = component.getParameters().stream().map(ComponentParameter::getPredicate).collect(Collectors.toSet());
        Set<PropertySingleValuePair> bindingPairs = shape.getPropertyValuePairSets().getAnnotations().stream()
                .filter(p -> parameterProperties.contains(p.getProperty()))
                .flatMap(p -> PropertySingleValuePair.create(p).stream())
                .collect(Collectors.toSet());

        if (bindingPairs.isEmpty()) {
            return ImmutableSet.of();
        }

        int maxParametersSize = parameterProperties.size();
        Set<Property> requiredParameters = component.getParameters().stream().filter(p -> !p.isOptional()).map(ComponentParameter::getPredicate).collect(Collectors.toSet());
        int requiredParameterSize = requiredParameters.size();

        ImmutableSet.Builder<Set<PropertySingleValuePair>> pairs = ImmutableSet.builder();
        for (int i = requiredParameterSize; i<=maxParametersSize && i <= bindingPairs.size(); i++ ) {
            pairs.addAll(Sets.combinations(bindingPairs, i));
        }

        return pairs.build().stream()
                .filter( pairA -> {
                    // delete pairs without the required parameters
                    List<Property> pairAProperties = pairA.stream().map(PropertySingleValuePair::getProperty).collect(Collectors.toList());

                    // check if it has duplicates (if set size is smaller that list size)
                    if (ImmutableSet.copyOf(pairAProperties).size() != pairAProperties.size()) {
                        return false;
                    }
                    // check if permutation has  required parameters
                    Set<Property> properties = new HashSet<>();
                    properties.addAll(pairAProperties);
                    properties.addAll(requiredParameters);
                    return properties.size() == pairAProperties.size();
                })
                .collect(Collectors.toSet());
    }
}
