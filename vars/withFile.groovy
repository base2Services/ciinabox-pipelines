/***********************************
with Each File Step DSL

executes a block for each file match a pattern

example usage
withFile('*.json') {
  echo "${filename}"
}
************************************/

def call(pattern, body) {
  new FileNameFinder().getFileNames(pattern).each { filename ->
    body()
  }
}
