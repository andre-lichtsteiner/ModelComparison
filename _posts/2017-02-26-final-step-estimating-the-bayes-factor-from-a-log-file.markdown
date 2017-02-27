---
layout: post

title: "Final Step - Estimating the Bayes factor from a log file"
subtitle:  "Model Comparison Tutorial"
date:   2017-02-26 13:41:20 +1300
permalink: /using-modelcomparison-calculator/
categories: tutorial
---

This is done via the command line.

Once the ModelComparison package is [installed]({{site.baseurl}}/installation/), you can use the built in calculator program to estimate the Baye factor from an analysis log file.

### Overview

You run the command, passing it the log file which you want to use as the parameter. 

It will give you the result, essentially averaging the values for U from the log file, but excluding those which are from the equilibration step.

For oneway analyses, where beta is only changed in one direction (up or down), there will be one result.

For bothways analyses, where beta is changed in one direction then changed back in reverse, there will be two results. 
It may make sense to average these or treat them as an interval, as they can indicate a type of rough bound on the estimate.

### Command line interface

This uses BEAST's built-in "appstore" functionality, which is essentially a way for packages to bundle additional programs which are a useful or necessary part of the package. 

On Windows:
{% highlight plaintext %}
\(your install directory)\BEAST\AppStore.exe ModelComparisonCalculator (log_file)
{% endhighlight %}

On Mac:
{% highlight plaintext %}
/Applications/BEAST 2.X.X/bin/appstore ModelComparisonCalculator (log_file)
{% endhighlight %}

On Linux:
{% highlight plaintext %}
/(your install directory)/beast/bin/appstore ModelComparisonCalculator (log_file)
{% endhighlight %}

The result on all platforms will look similar to:
{% highlight plaintext %}
Log file analysed. The log Bayes factor calculated is: 
X
{% endhighlight %}

If the above commands don't work, look for the appstore file in the BEAST directory, and run that with the parameters ModelComparisonCalculator and the log_file.

