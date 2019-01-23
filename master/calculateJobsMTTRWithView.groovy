import hudson.Util;
import hudson.model.*;
import hudson.plugins.nested_view.NestedView;

import java.time.Duration
import java.text.SimpleDateFormat

// hudson.model.FreeStyleBuild
def printBuildInfo = { Run build ->
  println 'Build number:' + build.number
  println 'Start time: ' + build.time

  if(build.result == null)
    println 'Status: ongoing'
  else
    println 'Status: ' + build.result

  println 'Duration: ' + build.getDurationString()
  println()
}

def formatDuration = { long duration ->
  return Util.getTimeSpanString(duration);
}

def calcAverageRecoveryTime = { totalFailedTime, buildCount ->
  if(buildCount == 0)
    return 0
  return totalFailedTime / buildCount
}

def printJobInfo = { Job job ->
  println('job name:' + job.name)

  // pipeline job org.jenkinsci.plugins.workflow.job.WorkflowJob
  println('\t job type:' + job.class)

  //println('\n\t description:' + job.description + '\n')
  println('\t isDisabled:' + job.disabled)
  println('\t' + job.url)

  // never null. The first entry is the latest build.
  def builds = job.getBuilds()

  def failedBuildTime = 0
  def failedBuildNumber = 0
  def totalRecoveryTime = 0
  def buildCount = 0

  def firstToLastBuilds = builds.reverse()
  firstToLastBuilds.iterator().each {

    def result = it.result
	if(result == null)
	  return
	
	// get the first FAILED build start time
	if(!result.equals(Result.SUCCESS)) {
	  if(failedBuildTime != 0)
	    return
      println "failed build: " + it.number
      failedBuildNumber = it.number
	  failedBuildTime = it.getStartTimeInMillis()
	  return
	}

    if(failedBuildTime == 0)
      return

	// SUCCESS
	recoveryTime = it.getStartTimeInMillis() - failedBuildTime
    println "recovery build: " + it.number
    println "recovery time from last failed build " + failedBuildNumber + ": " + formatDuration(recoveryTime)
	totalRecoveryTime += recoveryTime
	failedBuildTime = 0
	buildCount++
    //printBuildInfo(it)
  }
  println 'buildCount: ' + buildCount
  println 'totalRecoveryTime: ' + totalRecoveryTime
  long mttr = calcAverageRecoveryTime(totalRecoveryTime, buildCount)
  println 'mttr: ' + mttr
  def duration  = Duration.ofMillis(mttr)
  println formatDuration(duration.toMillis())
  println()
}

def getNestedView
getNestedView = { views, viewList ->
  println viewList.size


  if(viewList.size == 0)
    return views

  currentView = null
  views.each {
	if(it.name == viewList[0]) {
	  println 'match ' + it.name
	  viewList.remove(0)
	  currentView = it
    }
  }

  if(currentView instanceof NestedView) {
	return getNestedView(currentView.getViews(), viewList)
  }

  if(currentView instanceof ListView) {
    return currentView
  }
}

def run = { viewPath ->
  view = getNestedView(Hudson.instance.getViews(), viewPath)
  view.getItems().each {
    printJobInfo(it)
  }
}
