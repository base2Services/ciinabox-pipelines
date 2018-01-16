/***********************************
 ecr DSL

 Creates ECR repository

 example usage
 ecr
   accountId: '1234567890',
   region: 'ap-southeast-2',
   image: 'myrepo/image',
   otherAccountIds: ['0987654321','5432167890']
 )
 ************************************/

 def call(body) {
   def config = body
   def ecrRepo = "${config.accountId}.dkr.ecr.${config.region}.amazonaws.com/${config.image}"
   def policy = []
   sh "aws ecr create-repository --repository-name ${ecrRepo} --region ${config.region} || echo 'ECR repository ${ecrRepo} exists'"
   if config.otherAccountIds {
    config.otherAccountIds.each {
        policy +=  "{  \"Sid\": \"AllowPull\",  \"Effect\": \"Allow\",  \"Principal\": {\"AWS\": [\"arn:aws:iam::${it}:root\"]},  \"Action\": [    \"ecr:GetDownloadUrlForLayer\",    \"ecr:BatchGetImage\",    \"ecr:BatchCheckLayerAvailability\"  ]}"
    }
    policy_text="{\"Version\": \"2008-10-17\",\"Statement\": [${policy.join(",")}]}"
    sh """#!/bin/bash
    aws ecr set-repository-policy \
    --region ${config.region} \
    --registry-id ${config.accountId} \
    --repository-name ${ecrRepo} \
    --policy-text "${policy_text}"
    """
   }
 
 }
