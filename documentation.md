---
layout: page
title: Documentation
permalink: /documentation/

#Have used (??) where I am not sure of something
---

Here you will find the documentation for the Model Comparison package.

The concepts in this documentation are based on the method laid out in a paper by *{{site.lartillot_paper_authors}}* titled [{{site.lartillot_paper_title}}]({{site.lartillot_paper_link}})

## BEAST Objects
---
### ModelComparisonDistribution

This BEAST object is a Distribution, and it does the work of calculating the posterior using the beta value and the two provided Distribution inputs.

#### Options/Inputs
- betaParameter
  - This is a RealParameter (ie. a decimal number) which is used by ModelComparisonDistribution in calculating the posterior. 
  - Note:
  - This should be set to either 0 or 1 in your XML
    - Although you can choose other values if you wish, Model Comparison won't stop you. If you wanted to explore just a subset of the 0 to 1 space, you can do this by setting the value of the betaValue to something like 0.3, and then it will go from 0.3 to 0.7, or from 0.1 to 0.1 (is this useful to someone?) (??)
  - This is (??) required to be part of the state

- automaticallyControlBeta
  - This is a boolean (true or false) input which determines whether or not the Model Comparison package should control the value of the beta parameter so that it slowly changes over the course of the BEAST run
  - Note:
  - Before using Model Comparison to control the value of beta, you must first equilibrate your run using one of either 0 or 1

- distribution (x2)
  - You must provide exactly two distributions as input to the PowerCompound distribution
  - The first distribution will be raised to the power of (1 - beta) in the posterior, and the second distribution will be raised to the power of beta
  - (So when beta is equal to 0, the overall posterior will be exactly equal to the first distribution, as it will be raised to the power of 1, while the second distribution will be raised to the power of zero.)

#### Usage
{% highlight xml %}
<distribution
id="posterior"
spec="beast.math.distributions.ModelComparisonDistribution" 
automaticallyControlBeta="false" 
betaParameter="@betaParameter.Main">
	<distribution id="posterior_for_first_model"/>
	<distribution id="posterior_for_second_model"/>
</distribution>
{% endhighlight %}

---
### ModelComparisonMCMC

This replaces/extends (??) BEAST'S usual MCMC chain code to allow for the values of beta to be changed very slowly at every step of the chain.

(??) Why don't I have the automaticallyControlBeta input on ModelComparisonMCMC rather than on the distribution? Might make more sense?

#### Options/Inputs
- Generally the same as normal MCMC, but (??) haven't tested with certain options/inputs yet

#### Usage
Instead of using this:
{% highlight xml %}
<run chainLength="20000000" id="mcmc" spec="beast.core.MCMC">
{% endhighlight %}
Use this:
{% highlight xml %}
<run chainLength="20000000" id="mcmc" spec="beast.core.ModelComparisonMCMC">
{% endhighlight %}
---
### ModelComparisonLogger

This allows for logging the values of beta and U (as defined in the [{{site.lartillot_paper_authors_short}}]({{site.lartillot_paper_link}}) paper).

#### Options/Inputs
- posteriorDistribution
  - This is the ModelComparisonDistribution which is your posterior distribution.

#### Usage


Add the following to any logger (eg. the screenlog or tracelog logger:
{% highlight xml %}
<log spec="util.ModelComparisonLogger" posteriorDistribution="@posterior"/>
<!-- @posterior here means that our posterior object has the id "posterior" -->
{% endhighlight %}
Or alternatively add a new logger which saves only the beta and U values to a file:
{% highlight xml %}
<logger fileName="$(filebase)_beta_U.log" id="betaAndUValueLogger" logEvery="500">
	<log spec="util.ModelComparisonLogger" posteriorDistribution="@posterior"/>
	<!-- Here we have used $(filebase) in the filename - BEAST will replace that part with the filename of your XML file (without the .xml extension) -->
</logger>
{% endhighlight %}



