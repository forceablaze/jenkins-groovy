import hudson.model.*;

def printJobInfo = { Job job ->
  println('job name:' + job.name)

  // pipeline job org.jenkinsci.plugins.workflow.job.WorkflowJob
  println('\t job type:' + job.class)

  println('\n\t description:' + job.description + '\n')
  println('\t isDisabled:' + job.disabled)
  println('\t' + job.url)
  println()
}

def run = {
  Hudson.instance.getAllItems(Job.class).each {
    printJobInfo(it)
  }
}
