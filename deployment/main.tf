# Copyright (c) HashiCorp, Inc.
# SPDX-License-Identifier: MPL-2.0

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      hashicorp-learn = "lambda-api-gateway"
    }
  }

}

resource "random_pet" "lambda_bucket_name" {
  prefix = "learn-terraform-functions"
  length = 4
}

resource "aws_s3_bucket" "lambda_bucket" {
  bucket = random_pet.lambda_bucket_name.id
}

resource "aws_s3_bucket_ownership_controls" "lambda_bucket" {
  bucket = aws_s3_bucket.lambda_bucket.id
  rule {
    object_ownership = "BucketOwnerPreferred"
  }
}

resource "aws_s3_bucket_acl" "lambda_bucket" {
  depends_on = [aws_s3_bucket_ownership_controls.lambda_bucket]

  bucket = aws_s3_bucket.lambda_bucket.id
  acl    = "private"
}
resource "aws_s3_object" "lambda_hello_world" {
  bucket = aws_s3_bucket.lambda_bucket.id

  key    = "MyFirstGroovyProject-1.0-SNAPSHOT.jar"
  source = "${path.module}/../target/MyFirstGroovyProject-1.0-SNAPSHOT.jar"

  etag = "${filemd5("${path.module}/../target/MyFirstGroovyProject-1.0-SNAPSHOT.jar")}"
}


resource "aws_lambda_function" "hello_world" {
  function_name = "HelloWorld"

  s3_bucket = aws_s3_bucket.lambda_bucket.id
  s3_key    = aws_s3_object.lambda_hello_world.key
  environment {variables = {SUMMARY_BUCKET_NAME = aws_s3_bucket.summary_bucket.id} }

  runtime = "java17"
  handler = "org.example.MyHandler::handleEvent"

  source_code_hash = "${path.module}/../target/MyFirstGroovyProject-1.0-SNAPSHOT.jar.jar.output_base64sha256"

  role = aws_iam_role.lambda_exec.arn
  timeout = 30
  memory_size = 256
}

resource "aws_cloudwatch_log_group" "hello_world" {
  name = "/aws/lambda/${aws_lambda_function.hello_world.function_name}"

  retention_in_days = 30
}

resource "aws_iam_role" "lambda_exec" {
  name = "serverless_lambda"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Sid    = ""
      Principal = {
        Service = "lambda.amazonaws.com"
      }
    }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "lambda_policy" {
  role       = aws_iam_role.lambda_exec.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}
resource "aws_iam_role_policy_attachment" "lambda_policy_s3" {
  role       = aws_iam_role.lambda_exec.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonS3FullAccess"
}

resource "random_pet" "report_bucket_name" {
  prefix = "jest-test-report"
  length = 4
}

resource "aws_s3_bucket" "report_bucket" {
  bucket = random_pet.report_bucket_name.id
}

resource "aws_s3_bucket_ownership_controls" "report_bucket" {
  bucket = aws_s3_bucket.report_bucket.id
  rule {
    object_ownership = "BucketOwnerPreferred"
  }
}

resource "aws_s3_bucket_acl" "report_bucket" {
  depends_on = [aws_s3_bucket_ownership_controls.report_bucket]

  bucket = aws_s3_bucket.report_bucket.id
  acl    = "private"
}

resource "aws_s3_bucket_notification" "report_s3_trigger" {
  bucket = aws_s3_bucket.report_bucket.id
  lambda_function {
    lambda_function_arn = aws_lambda_function.hello_world.arn
    events = ["s3:ObjectCreated:*"]
  }

}
resource "aws_lambda_permission" "s3_lambda_permission" {
  statement_id = "AllowExecutionFromS3Bucket"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.hello_world.arn
  principal     = "s3.amazonaws.com"
  source_arn = aws_s3_bucket.report_bucket.arn
}

resource "random_pet" "summary_bucket_name" {
  prefix = "summary-report"
  length = 4
}

resource "aws_s3_bucket" "summary_bucket" {
  bucket = random_pet.summary_bucket_name.id
}

resource "aws_s3_bucket_ownership_controls" "summary_bucket" {
  bucket = aws_s3_bucket.summary_bucket.id
  rule {
    object_ownership = "BucketOwnerPreferred"
  }
}

resource "aws_s3_bucket_acl" "summary_bucket" {
  depends_on = [aws_s3_bucket_ownership_controls.summary_bucket]

  bucket = aws_s3_bucket.summary_bucket.id
  acl    = "private"
}