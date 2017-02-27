package beast.math.distributions;


import beast.core.Citation;
import beast.core.Distribution;
import beast.core.Input;
import beast.core.ModelComparisonMCMC;
import beast.core.parameter.BooleanParameter;
import beast.core.parameter.RealParameter;
import beast.core.util.CompoundDistribution;

import java.util.NoSuchElementException;

/**
 * Created by Andre Lichtsteiner (https://andre-lichtsteiner.github.io/)
 * This class extends the CompoundDistibution, but replaces the logP mechanism with one which
 * uses the value of beta to control the powers to which the inner distributions are raised.
 */

@Citation("Lartilot and Philippe (2006) 'Computing Bayes Factors Using Thermodynamic Integration''")
public class ModelComparisonDistribution extends CompoundDistribution{

   public Input<RealParameter> betaParameterInput = new Input<>("betaParameter", "Beta parameter as in the paper by Lartillot and Philippe. ");

   private double[] innerPosteriorLogP;

   private double betaValue;

   public double getBetaValue(){
       return betaValue;
   }

    public void setBetaValue(double newValue){
        betaValue = newValue;
    }


    @Override
    public void initAndValidate(){
        super.initAndValidate();

        innerPosteriorLogP = new double[2]; //Set up empty

        if (pDistributions.get().size() != 2){
            System.out.println("Must provide exactly two distributions for ModelComparisonDistribution.");
            throw new IndexOutOfBoundsException("Wrong number of distributions provided.");
        }

    }

    public void cacheInnerLogPValues(double[] newInnerLogP){
        innerPosteriorLogP = newInnerLogP.clone();
    }

    public double[] getInnerPosteriorLogP(){
        return innerPosteriorLogP;
    }

    public double calculateLogPFromInnerLogPValues(double[] innerLogPValues){

        double totalLogP = 0;

        totalLogP = ((1 - betaValue) * innerLogPValues[0]);
        totalLogP = totalLogP + (betaValue * innerLogPValues[1]);
        logP = totalLogP;
        return totalLogP;

    }

    public double calculateU(){
        double UValue = innerPosteriorLogP[1] - innerPosteriorLogP[0];
        //POTENTIAL for caching issues here!
        ///The above should be (but possibly might not be) equal to //double UValue = pDistributions.get().get(1).calculateLogP() - pDistributions.get().get(0).calculateLogP();

        //if (UValue != altUValue){
         //   System.out.println("CACHED VALUES OF INNER POSTERIOR IS NOT WHAT IT SHOULD BE!");
       // }

        return UValue;
    }

    @Override
    public double calculateLogP(){
       //Calculate separately for dist 0 and dist 1
        double[] logPArray = new double[2];

        int which_dist = 0;
        for (Distribution dists : pDistributions.get()) {
            if (dists.isDirtyCalculation()) {
                logPArray[which_dist] += dists.calculateLogP();
            } else {
                logPArray[which_dist] += dists.getCurrentLogP();
            }

            /*
            //Curent caching is not suitable for replacing with cached values...
            if (logPArray[which_dist] != innerPosteriorLogP[which_dist]){
                System.out.println("The value for posteriorLogP was DIFFERENT from the cached value");
            }
            else{
                System.out.println("Values same");
            }
            */

            if (Double.isInfinite(logPArray[which_dist]) || Double.isNaN(logPArray[which_dist])) {
               return logPArray[which_dist];
            }
            which_dist = which_dist + 1;
        }

        //Perform the power step
        //We just multiply by beta in log space
        //logP is an important variable name to use as it is declared in the Distribution (super) class, and it is loggable through that

        logP = calculateLogPFromInnerLogPValues(logPArray);
        return logP;



    }
}
