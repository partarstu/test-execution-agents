# API Test Execution Agent

## Overview
This module provides the API Test Execution Agent capabilities, enabling automated API testing through LLM-driven orchestration.

## Features
- **HTTP Request Execution:** Supports GET, POST, PUT, DELETE, etc.
- **Authentication:** Supports Basic (Preemptive), Bearer Token, and API Key (Header/Query).
- **Context Management:** Maintains cookies, session variables, and base configuration across steps.
- **Data Driven Testing:** Loads test data from JSON and CSV files.
- **Assertions:** Validates Status Codes, JSON Paths, JSON Schemas, and OpenAPI Specifications.
- **Variable Substitution:** Dynamically replaces `${variableName}` in URLs, Headers, and Bodies.

## Implementation Status
- [x] Module Structure
- [x] Dependencies
- [x] Core Components
    - [x] ApiContext
    - [x] ApiRequestTools
    - [x] ApiDataTools
    - [x] ApiAssertionTools
    - [x] ApiTestStepActionAgent
- [x] Agent Integration (Wiring in `Agent.java`)
- [x] Testing (Unit tests for Request Tools)

## Usage
The `Agent.executeTestCase(String message)` method is the entry point. It parses the user message to extract a Test Case, initializes the `ApiContext`, and executes the steps using the `ApiTestStepActionAgent`.