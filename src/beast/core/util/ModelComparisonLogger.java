package beast.core.util;

import beast.core.BEASTObject;
import beast.core.Input;
import beast.core.Loggable;
import beast.math.distributions.PowerCompoundDistribution;

import java.io.PrintStream;

/**
 * Created by andre on 3/02/17.
 */
public class ModelComparisonLogger extends BEASTObject implements Loggable {

    public Input<PowerCompoundDistribution> posteriorInput = new Input<>("posteriorDistribution", "The BEASTObject which is the posterior. Must be a PowerCompoundDistribution");

    public PowerCompoundDistribution posteriorObject;

    @Override
    public void initAndValidate(){
        posteriorObject = posteriorInput.get();
    }

    @Override
    public void init(PrintStream out) {
        out.print("betaValue\tU value\t");
    }

    @Override
    public void log(int sample, PrintStream out) {
        double betaValue = posteriorObject.betaValue;
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
