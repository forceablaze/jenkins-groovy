def List<Thread> threads = []
def scriptList = [:]
def outputMap = [:]

ruby_version = 'def proc = "ruby --version".execute(); proc.waitFor(); println proc.in.text';
python_version = 'def proc = ["python",  "-c", "import platform;print(platform.python_version())"].execute(); proc.waitFor(); println proc.in.text';
svn_version = 'def proc = "svn --version".execute(); proc.waitFor(); def str = proc.in.text; println str.substring(0, str.indexOf(10))';
print_ip = 'println InetAddress.localHost.hostAddress';
print_hostname = 'println InetAddress.localHost.canonicalHostName';

scriptList << ['print_hostname':print_hostname]
scriptList << ['print_ip':print_ip]
scriptList << ['ruby_version':ruby_version]
scriptList << ['python_version':python_version]
scriptList << ['svn_version':svn_version]

def executeScript = { String key, String script ->

  t = Thread.start {
    def stringWriter = new StringWriter()
    def shellBinding = new Binding(out: new PrintWriter(stringWriter))

    outputMap << [(key):stringWriter]

    // Create GroovyShell to evaluate script.
    def shell = new GroovyShell(shellBinding)
    shell.evaluate(script)
  }
  threads << t
}

scriptList.each { k, v ->
  executeScript(k, v)
}

threads.each { t ->
  t.join()
}

outputMap.each { k, v ->
  println v.toString()
}
