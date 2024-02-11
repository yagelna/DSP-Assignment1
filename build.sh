#!/bin/bash

# Set your Amazon ECR repository name
ECR_REPOSITORY="assignments"

# Set your AWS region
AWS_REGION="us-east-1"

# Set your Docker image tag
IMAGE_TAG="latest"

# Set your Docker image name
IMAGE_NAME="assignment1"

AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query "Account" --output text)

# Check if the ECR repository exists, if not, create it
if ! aws ecr describe-repositories --repository-names "$ECR_REPOSITORY" --region "$AWS_REGION" > /dev/null 2>&1; then
    echo "ECR repository '$ECR_REPOSITORY' not found. Creating..."
    if aws ecr create-repository --repository-name "$ECR_REPOSITORY" --region "$AWS_REGION" > /dev/null 2>&1; then
        echo "ECR repository '$ECR_REPOSITORY' created successfully."
    else
        echo "Failed to create ECR repository '$ECR_REPOSITORY'."
        exit 1
    fi
else
    echo "ECR repository '$ECR_REPOSITORY' already exists."
fi

# Build Docker image
echo "Building Docker image '$IMAGE_NAME'..."
if ! docker buildx build --platform linux/amd64 -t "$IMAGE_NAME" .; then
    echo "Failed to build Docker image '$IMAGE_NAME'. Aborting."
    exit 1
else
    echo "Docker image '$IMAGE_NAME' built successfully."
fi

# Authenticate Docker to your ECR
echo "Authenticating Docker to Amazon ECR..."
if ! aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com"; then
    echo "Failed to authenticate Docker to Amazon ECR."
    exit 1
else
    echo "Docker authenticated successfully to Amazon ECR."
fi

# Tag Docker image
echo "Tagging Docker image '$IMAGE_NAME' as '$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPOSITORY:$IMAGE_TAG'..."
docker tag "$IMAGE_NAME:$IMAGE_TAG" "$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPOSITORY:$IMAGE_TAG"
echo "Docker image tagged successfully."

# Push Docker image to ECR
echo "Pushing Docker image '$IMAGE_NAME' to Amazon ECR..."
if ! docker push "$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPOSITORY:$IMAGE_TAG"; then
    echo "Failed to push Docker image to Amazon ECR."
    exit 1
else
    echo "Docker image '$IMAGE_NAME' pushed successfully to Amazon ECR."
fi