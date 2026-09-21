// The words that go with each demo job. What actually runs (command, request,
// timeout, retries) is defined by the API, which serves it at /api/demo/jobs;
// ids here must match the ids there.
export type DemoContent = {
  id: string;
  title: string;
  summary: string;
  steps: string[];
  expected: string;
};

export const DEMO_CONTENT: DemoContent[] = [
  {
    id: "http-get",
    title: "HTTP GET",
    summary: "Send a GET request and capture the response.",
    steps: [
      "Runway sends a GET request to httpbin.org.",
      "httpbin echoes back what it received: your query string, request headers and origin.",
      "Runway logs the status code and response body, and marks the run SUCCESS for any 2xx response.",
    ],
    expected: "Status SUCCESS in about a second, with the echoed JSON in the output.",
  },
  {
    id: "http-post",
    title: "HTTP POST",
    summary: "POST a JSON body and see it echoed back.",
    steps: [
      "Runway sends a POST request to httpbin.org with a JSON body and a Content-Type header.",
      "httpbin parses the body and returns it under the \"json\" key of its response.",
      "Runway logs the status code and response body.",
    ],
    expected: "Status SUCCESS, and the output contains the exact JSON that was sent.",
  },
  {
    id: "shell-live-logs",
    title: "Shell live logs",
    summary: "Run a shell script that prints progress once a second.",
    steps: [
      "Runway runs the command in a shell on the Runway server.",
      "Each line of output is streamed to you as the script prints it, not after it finishes.",
      "Anything written to stderr is shown in red; the run succeeds because the script exits with code 0.",
    ],
    expected: "Five step lines one second apart, a red warning line, then \"Done.\" and SUCCESS with exit code 0.",
  },
  {
    id: "http-retry",
    title: "Retry on failure",
    summary: "Call an endpoint that always fails and watch Runway retry it.",
    steps: [
      "Runway sends a GET request to an httpbin endpoint that always answers HTTP 500.",
      "A non-2xx response counts as a failure. The job allows 3 attempts, so Runway waits 5 seconds and tries again.",
      "This repeats until attempt 3 fails, and then Runway gives up.",
    ],
    expected:
      "The status flips to RETRYING between attempts, the attempt counter reaches 3 of 3, and the run ends FAILED after about 10 seconds.",
  },
  {
    id: "http-timeout",
    title: "Timeout",
    summary: "Call a slow endpoint with a timeout shorter than its response time.",
    steps: [
      "Runway sends a GET request to an httpbin endpoint that waits 10 seconds before responding.",
      "The job has a 3 second timeout, so Runway abandons the request before a response arrives.",
    ],
    expected: "After about 3 seconds the run ends with status TIMEOUT and an error explaining the limit was exceeded.",
  },
];
