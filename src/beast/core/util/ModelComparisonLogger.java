package beast.core.util;

import beast.core.BEASTObject;
import beast.core.Input;
import beast.core.Loggable;
import beast.math.distributions.ModelComparisonDistribution;

import java.io.PrintStream;

/**
 * Created by andre on 3/02/17.
 */
public class ModelComparisonLogger extends BEASTObject implements Loggable {

    public Input<ModelComparisonDistribution> posteriorInput = new Input<>("posteriorDistribution", "The BEASTObject which is the posterior. Must be a ModelComparisonDistribution");

    public ModelComparisonDistribution posteriorObject;

    @Override
    public void initAndValidate(){
        posteriorObject = posteriorInput.get();
    }

    @Override
    public void init(PrintStream out) {
        out.print("BetaValue\tUValue\t");
    }

    @Override
    public void log(int sample, PrintStream out) {
        double betaValue = posteriorObject.getBetaValue();
        out.print(betaValue + "\t");
        out.print(calculateUValue() + "\t");
    }

    public double calculateUValue(){

        return posteriorObject.calculateU();

    }

    @Override
    public void close(PrintStream out) {

    }
}
