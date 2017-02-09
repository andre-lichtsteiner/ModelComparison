package beast.math.distributions;


import java.util.List;
import java.util.Random;

import beast.core.BEASTObject;
import beast.core.Description;
import beast.core.Distribution;
import beast.core.Function;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.core.State;
import beast.core.parameter.IntegerParameter;
import beast.core.parameter.RealParameter;
import beast.math.distributions.ParametricDistribution;
import beast.math.distributions.Prior;


@Description("Produces prior (log) probability of value x." +
        "If x is multidimensional, the components of x are assumed to be independent, " +
        "so the sum of log probabilities of all elements of x is returned as the prior.")
public class CustomPrior extends Prior {
    //final public Input<Function> m_x = new Input<>("param", "point at which the density is calculated", Validate.REQUIRED);
    final public Input<IntegerParameter> m_dim = new Input<>("dim", "Index into the multidimensional RealParameter");
   // final public Input<ParametricDistribution> distInput = new Input<>("distr", "distribution used to calculate prior, e.g. normal, beta, gamma.", Validate.REQUIRED);


    /**
     * shadows m_distInput *
     */
    protected ParametricDistribution dist;
    protected int dim;

    @Override
    public void initAndValidate() {
        dist = distInput.get();
        if (m_dim.get() != null){
            dim = m_dim.get().getValue();
        }
        calculateLogP();
    }

    @Override
    public double calculateLogP() {
        Function x = m_x.get();
        if (x instanceof RealParameter || x instanceof IntegerParameter) {
            // test that parameter is inside its bounds
            double l = 0.0;
            double h = 0.0;
            if (x instanceof RealParameter) {
                l = ((RealParameter) x).getLower();
                h = ((RealParameter) x).getUpper();
            } else {
                l = ((IntegerParameter) x).getLower();
                h = ((IntegerParameter) x).getUpper();
            }
            for (int i = 0; i < x.getDimension(); i++) {
                double value = x.getArrayValue(i);
                if (value < l || value > h) {
                    logP = Double.NEGATIVE_INFINITY;
                    return Double.NEGATIVE_INFINITY;
                }
            }
            if (x.getDimension() > 1 && m_dim.get() != null){
                //Need to use the dim input to get the right value
                //Need to reduce the function down to only have the one value (default is all values in the input)
                x = new SingletonFunction(x.getArrayValue(dim));
            }
        }

        logP = dist.calcLogP(x);
        return logP;
    }

    /** return name of the parameter this prior is applied to **/

    public String getParameterName() {
        if (m_x.get() instanceof BEASTObject) {
            return ((BEASTObject) m_x.get()).getID();
        }
        return m_x.get() + "";
    }

    @Override
    public void sample(State state, Random random) {
    }

    @Override
    public List<String> getArguments() {
        return null;
    }

    @Override
    public List<String> getConditions() {
        return null;
    }

    private class SingletonFunction implements Function {
        double stored_value;

        @Override
        public int getDimension() {
            return 1;
        }

        @Override
        public double getArrayValue() {
            return stored_value;
        }

        @Override
        public double getArrayValue(int dim) {
            if (dim == 0){
                return stored_value;
            }
            else{
                return Double.NEGATIVE_INFINITY; //Probs ok
            }
        }

        public SingletonFunction(double value){
            stored_value = value;
        }
    }
}
