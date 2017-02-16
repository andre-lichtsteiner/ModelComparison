---
layout: page
title: Documentation
permalink: /documentation/

#Have used (??) where I am not sure of something
---

Here you will find the documentation for the Model Comparison package.

The concepts in this documentation are based on the method laid out in a paper by *{{site.lartillot_paper_authors}}* titled [{{site.lartillot_paper_title}}]({{site.lartillot_paper_link}})

## Installation

Currently cannot be installed.... Only the lucky few can use this... (??)

## Basics of Usage

This package can be used to calculate the Bayes factor of one model over another.
It can be used in certain cases where path sampling fails, particularly where sampling from the prior is simply impractical.

One use of this is for structured trees (eg. using [MultiTypeTree](https://tgvaughan.github.io/MultiTypeTree/)) which become challenging when there are more than ust a few demes.

It is also worth checking out the [tutorials](/tutorials/).

**General overview of how this package can be used:**

1. Install the ModelComparison package (?? More details required - maybe even a separate page?)
2. Setup a BEAST XML file which specifies your analysis (see tutorials and below documentation)
3. Equilibrate the run (run an initial chain until equilibrated) (?? More detail)
4. Modify one value in the XML file (telling ModelComparison to progressively switch between your two models)
5. Resume the run using the changed XML file 
6. Run the command line tool (built-in) to estimate the Bayes factor using the log file

## Overview of the package

This package uses:

- a customised version of BEAST's MCMC chain which allows the beta value to be changed incrementally and evenly throughout the course of the run.
- a customised distribution which allows you to switch between two posteriors depending on the value of beta
- a logger which records the beta and U values as often as you wish
- a tool which uses the recorded beta and U values to estimate the Bayes factor

You must specify the first three of these to be used within your BEAST XML file or the run will not result in a comparison of the two models.

## BEAST Objects
---
### ModelComparisonDistribution

This BEAST object is a Distribution, and it does the work of calculating the posterior using the beta value and the two provided Distribution inputs.

#### Options/Inputs
- betaParameter
  - This is a RealParameter (ie. a decimal number) which is used by ModelComparisonDistribution in calculating the posterior. 
  - Note:
  - This should be set to either 0 or 1 in your XML
    - Although you can choose other values if you wish, ModelComparison won't stop you. If you wanted to explore just a subset of the 0 to 1 space, you can do this by setting the value of the betaValue to something like 0.3, and then it will go from 0.3 to 0.7, or from 0.1 to 0.1 (is this useful to someone?) (?? Also does it still work this way?)
  - This is (??) required to be part of the state

- betaControlMode
  - This is a text input which determines how the ModelComparison package should control the value of the beta parameter (if at all) so that it slowly changes over the course of the BEAST run
  - Note:
  - Before using Model Comparison to control the value of beta, you must first equilibrate your run using one of either 0 or 1

- distribution (x2)
  - You must provide exactly two distributions as input to the ModelComparisonDistribution
  - The first distribution will be raised to the power of (1 - beta) in the posterior, and the second distribution will be raised to the power of beta
  - (So when beta is equal to 0, the overall posterior will be exactly equal to the first distribution, as it will be raised to the power of 1, while the second distribution will be raised to the power of zero.)

#### Usage
{% highlight xml %}
<distribution
id="posterior"
spec="beast.math.distributions.ModelComparisonDistribution" 
betaControlMode="false" 
betaParameter="@betaParameter.Main">
	<distribution id="posterior_for_first_model"/>
	<distribution id="posterior_for_second_model"/>
</distribution>
{% endhighlight %}

---
### ModelComparisonMCMC

This extends BEAST'S usual MCMC chain code to allow for the values of beta to be changed very slowly at every step of the chain.

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

(??) This seems to have been put in the wrong folder currently...

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



