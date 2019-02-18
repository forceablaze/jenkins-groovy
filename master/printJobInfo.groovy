import hudson.model.*;

import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.Instant

def printlnJSONPrefix = { text ->
  println('JSONSTRING:' + text)
}

def printJobInfo = { Job job ->

  printlnJSONPrefix('  \'job_name\':\'' + job.name + '\',')

  def lastBuild = job.getLastBuild()
  long startTime = lastBuild.startTimeInMillis

  def time = ZonedDateTime.ofInstant(
    Instant.ofEpochMilli(startTime), ZoneId.systemDefault())

  if(lastBuild.result == null)
    lastResult = 'Ongoing'
  else
    lastResult = lastBuild.result


  printlnJSONPrefix('  \'last_status\':\'' + lastResult + '\',')
  printlnJSONPrefix('  \'last_start_time\':\'' + time + '\'')
}

def run = { jobName = '', fromDateString, toDateString ->
  if(jobName == '')
    return

  def job = Hudson.instance.getItemByFullName(jobName)
  if(job == null)
    return

  printlnJSONPrefix('{')
  printJobInfo(job)
  printlnJSONPrefix('}')
}
