package beast.math.distributions;

import beast.core.CalculationNode;
import beast.core.Input;

/**
 * Created by andre on 3/02/17.
 */
public class BetaCalculator extends CalculationNode {
    public Input<Double> betaValueInput = new Input<>("betaValue", "The beta value to start with.");

    @Override
    protected void store() {
        super.store();
    }

    @Override
    protected boolean requiresRecalculation() {
        return super.requiresRecalculation();
    }

    @Override
    protected void restore() {
        super.restore();
    }

    @Override
    protected void accept() {
        super.accept();
    }

    @Override
    public void initAndValidate() {
        System.out.println("Init and validate called on BetaCalculator.");
        System.out.println("Beta value: " + betaValueInput.get().doubleValue());
    }
}
