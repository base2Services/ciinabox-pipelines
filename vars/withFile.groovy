/***********************************
with Each File Step DSL

executes a block for each file match a pattern

example usage
withFile('*.json') {
  echo "${filename}"
}
************************************/

def call(pattern, body) {
  def currentDir = new File()
  new FileNameFinder().getFileNames("${currentDir.absolutePath}/",pattern).each { filename ->
    body()
  }
}
