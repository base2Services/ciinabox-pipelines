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

  //sh "find ${currentDir} -name '${filePattern}' | xargs grep '${pattern}' | cut -d ':' -f 1 | rev | cut -d '/' -f 1 | rev > matches.txt"
  withEnv(["CURRENT_DIR=${currentDir}", "FILEPATTERN=\"${filePattern}\"", "PATTERN=${pattern}"]) {
    sh '''
      if [ -f matches.txt ]; then
        rm matches.txt
      fi

      if [ -f checking.txt ]; then
        rm checking.txt
      fi

      echo $FILEPATTERN

      touch matches.txt
      touch checking.txt

      find ${CURRENT_DIR} -name ${FILEPATTERN} > checking.txt
      while read e; do
        echo "checking $e for ${PATTERN}"
        grep -q "${PATTERN}" $e && echo $e | rev |  cut -d '/' -f 1 | rev >> matches.txt
      done <checking.txt
    '''
  }
  def matching = readFile('matches.txt').split("\r?\n")
  sh 'rm matches.txt checking.txt'
  if(matching.size()==1 && matching[0]==''){
    return []
  }
  matching
}
