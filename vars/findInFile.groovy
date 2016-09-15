/***********************************
with Each File Step DSL

find  a block for each file match a pattern

example usage
def environments = findInFile("stringineedtofind") {
  filenameFilter = '*.json'
}
************************************/

def call(pattern, body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  def currentDir = config.get('dir',pwd())
  def filePattern = config.get('filenameFilter', '.*')

  sh "find ${currentDir} -name '${filePattern}' | xargs grep '${pattern}' | cut -d ':' -f 1 | rev | cut -d '/' -f 1 | rev > matches.txt"
  def matching = readFile('matches.txt').split("\r?\n")
  sh 'rm matches.txt'
  matching
}
