package com.base2.ciinabox

class JobHelper {

  public static repoName(jobName) {
    def tokens = jobName.tokenize('/')
    def org = tokens[tokens.size()-3]
    def repo = tokens[tokens.size()-2]
    def branch = tokens[tokens.size()-1]
    repo
  }

}
