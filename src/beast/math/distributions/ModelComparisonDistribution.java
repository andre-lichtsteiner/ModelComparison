package beast.math.distributions;


import beast.core.Citation;
import beast.core.Distribution;
import beast.core.Input;
import beast.core.parameter.BooleanParameter;
import beast.core.parameter.RealParameter;
import beast.core.util.CompoundDistribution;

import java.util.NoSuchElementException;

/**
 * Created by andre on 2/02/17.
 */

@Citation("Lartilot and Philippe (2006) 'Computing Bayes Factors Using Thermodynamic Integration''")
public class ModelComparisonDistribution extends CompoundDistribution{

    public Input<RealParameter> betaParameterInput = new Input<>("betaParameter", "Beta parameter as in the paper by Lartillot and Philippe. ");
    public Input<BooleanParameter> betaControlAutomaticallyInput = new Input<>("automaticallyControlBeta", "If this is true, beta will automatically and continuously change from the value provided to beta parameter and one minus that value.", new BooleanParameter("false"));

    public boolean betaControlAutomatically;
    public double betaValue;
    public double betaIncrement;
    private double[] innerPosteriorLogP;
    //private Distribution[] likelihoodDists;
    //private Distribution[] priorDists;


    @Override
    public void initAndValidate(){
        super.initAndValidate();

        innerPosteriorLogP = new double[2]; //Set up empty

        if (pDistributions.get().size() != 2){
            System.out.println("Must provide exactly two distributions for ModelComparisonDistribution.");
            throw new IndexOutOfBoundsException("Wrong number of distributions provided.");
        }

        if (betaControlAutomaticallyInput.get() != null){
            betaControlAutomatically = betaControlAutomaticallyInput.get().getValue();
        }

        betaValue = betaParameterInput.get().getValue(); // Just to set its starting value

    }

    public void cacheInnerLogPValues(double[] newInnerLogP){
        innerPosteriorLogP = newInnerLogP.clone();
    }

    public double[] getInnerPosteriorLogP(){
        return innerPosteriorLogP;
    }

    public double calculateLogPFromInnerLogPValues(double[] innerLogPValues){

        double totalLogP = 0;
        if ( ! betaControlAutomatically){
            betaValue = betaParameterInput.get().getValue();
            // betaParameterValue = betaValue;
        }


        totalLogP = ((1 - betaValue) * innerLogPValues[0]);
        totalLogP = totalLogP + (betaValue * innerLogPValues[1]);
        logP = totalLogP;
        return totalLogP;

    }

    public double calculateU(){

        //Need to get separate values for the likelihoods and the priors inside each of the inner posteriors
        //Perhaps we don't actually need to do that? Because of the log-ness of it all

        /*
        //Used to use this
        if(likelihoodDists == null || priorDists == null) { // Need to first initialise these
            likelihoodDists = new Distribution[2];
            priorDists = new Distribution[2];
            for (int i = 0; i < pDistributions.get().size(); i++) {

                for (int j = 0; j < 2; j++) {
                    CompoundDistribution dist = (CompoundDistribution) pDistributions.get().get(i);
                    if (dist.pDistributions.get().get(j).getID().startsWith("likelihood")) {
                        likelihoodDists[i] = dist.pDistributions.get().get(j);
                    } else if (dist.pDistributions.get().get(j).getID().startsWith("prior")) {
                        priorDists[i] = dist.pDistributions.get().get(j);
                    } else {
                        throw new NoSuchElementException("Could not find a distribution with ID starting with either 'prior' or 'likelihood' inside one of the inner posteriors in the ModelComparisonDistribution.");
                    }
                }
            }
        }
        */

        //Now calculate the U value
        //double UValue = likelihoodDists[1].calculateLogP() + priorDists[1].calculateLogP();
        //UValue = UValue - (likelihoodDists[0].calculateLogP() + priorDists[0].calculateLogP());
        //The above is the same as: logP[1] = logP[0]


        //TODO later on confirm that there are no issues resulting from this using cached values
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
        //if (ignore) {      //Don't see the point of the ignore option?
        //    return logP;
        //}

        // Only using one thread, not yet implemented any multithreaded option
        /*
        int workAvailable = 0;
        if (useThreads) {
            for (Distribution dists : pDistributions.get()) {
                if (dists.isDirtyCalculation()) {
                    workAvailable++;
                }
            }
        }
        if (useThreads && workAvailable > 1) {
            logP = calculateLogPUsingThreads();
        }
        */
        // Only using one thread, this is the below
       // else {
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

                //What is the below bit for exactly?
                if (Double.isInfinite(logPArray[which_dist]) || Double.isNaN(logPArray[which_dist])) {
                   return logPArray[which_dist];
                }
                which_dist = which_dist + 1;
            }

    //Perform the power thingy
        //I think we just multiply by beta in log space?
        //logP is an important variable to use as it is declared in the Distribution (super) class, and it is loggable through that

        logP = calculateLogPFromInnerLogPValues(logPArray);

        //TEMPORARILY JUST IGNORE ONE OF THE TWO DISTS
       // logP = logPArray[0];

      //  }


        return logP;



    }
}
