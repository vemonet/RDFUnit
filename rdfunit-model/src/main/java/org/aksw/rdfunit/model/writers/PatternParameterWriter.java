package org.aksw.rdfunit.model.writers;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.vocabulary.DCTerms;
import com.hp.hpl.jena.vocabulary.RDF;
import org.aksw.rdfunit.model.interfaces.PatternParameter;
import org.aksw.rdfunit.vocabulary.RDFUNITv;

/**
 * Description
 *
 * @author Dimitris Kontokostas
 * @since 6/17/15 5:57 PM
 * @version $Id: $Id
 */
public final class PatternParameterWriter implements ElementWriter {

    private final PatternParameter patternParameter;

    private PatternParameterWriter(PatternParameter patternParameter) {
        this.patternParameter = patternParameter;
    }

    /**
     * <p>create.</p>
     *
     * @param patternParameter a {@link org.aksw.rdfunit.model.interfaces.PatternParameter} object.
     * @return a {@link org.aksw.rdfunit.model.writers.PatternParameterWriter} object.
     */
    public static PatternParameterWriter create(PatternParameter patternParameter) {return new PatternParameterWriter(patternParameter);}

    /** {@inheritDoc} */
    @Override
    public Resource write(Model model) {
        Resource resource = ElementWriter.copyElementResourceInModel(patternParameter, model);

        resource
                .addProperty(RDF.type, RDFUNITv.Parameter)
                .addProperty(DCTerms.identifier, patternParameter.getId())
                .addProperty(RDFUNITv.parameterConstraint, model.createResource(patternParameter.getConstraint().getUri()));

        if (patternParameter.getConstraintPattern().isPresent()) {
            resource.addProperty(RDFUNITv.constraintPattern, patternParameter.getConstraintPattern().get());
        }

        return resource;
    }
}