import { apiFetch } from "@/app/lib/api";
import { DEMO_CONTENT } from "@/app/lib/demoJobs";
import type { DemoJobDefinition } from "@/app/lib/types";
import { DemoRunner } from "@/app/components/DemoRunner";

function requestPreview(definition: DemoJobDefinition) {
  if (definition.type === "SHELL") {
    return definition.configuration.command;
  }
  const { method, url, headers, body } = definition.configuration;
  return [
    `${method} ${url}`,
    ...Object.entries(headers ?? {}).map(([key, value]) => `${key}: ${value}`),
    ...(body ? ["", body] : []),
  ].join("\n");
}

function retryLabel({ maxAttempts, strategy, initialDelaySeconds }: DemoJobDefinition["retryPolicy"]) {
  if (maxAttempts <= 1) {
    return "None";
  }
  return `${maxAttempts} attempts, ${strategy === "FIXED" ? "fixed" : "exponential"} ${initialDelaySeconds}s delay`;
}

export default async function DemoPage() {
  const definitions = await apiFetch<DemoJobDefinition[]>("/api/demo/jobs", { auth: false });

  const demos = DEMO_CONTENT.map((content) => {
    const definition = definitions.find((candidate) => candidate.id === content.id);
    if (!definition) {
      throw new Error(`The API has no demo job with id "${content.id}"`);
    }
    return { content, definition };
  });

  return (
    <div className="flex max-w-3xl flex-col gap-8">
      <div>
        <h1 className="text-2xl font-semibold">Demo</h1>
        <p className="mt-1 text-foreground/60">
          Pick a ready-made job, read what it does, and run it. These jobs are read-only, and the output shows
          up right inside each one as it runs. No account needed.
        </p>
      </div>

      <ul className="divide-y divide-foreground/10 rounded-lg border border-foreground/10">
        {demos.map(({ content, definition }) => (
          <li key={content.id}>
            <a
              href={`#${content.id}`}
              className="flex items-center justify-between gap-4 px-4 py-3 hover:bg-surface"
            >
              <span>
                <span className="font-medium">{content.title}</span>
                <span className="block text-sm text-foreground/60">{content.summary}</span>
              </span>
              <span className="text-xs text-foreground/60">{definition.type}</span>
            </a>
          </li>
        ))}
      </ul>

      {demos.map(({ content, definition }) => (
        <section
          key={content.id}
          id={content.id}
          className="flex scroll-mt-6 flex-col gap-4 rounded-lg border border-foreground/10 bg-surface p-6"
        >
          <div>
            <h2 className="text-lg font-semibold">{content.title}</h2>
            <p className="text-foreground/60">{content.summary}</p>
          </div>

          <div>
            <h3 className="mb-1 text-sm font-medium">What it does</h3>
            <ol className="list-decimal space-y-1 pl-5 text-sm">
              {content.steps.map((step) => (
                <li key={step}>{step}</li>
              ))}
            </ol>
          </div>

          <div>
            <h3 className="mb-1 text-sm font-medium">What you should see</h3>
            <p className="text-sm">{content.expected}</p>
          </div>

          <div>
            <h3 className="mb-1 text-sm font-medium">{definition.type === "SHELL" ? "Command" : "Request"}</h3>
            <pre className="overflow-x-auto whitespace-pre-wrap break-all rounded-md bg-black/90 p-3 font-mono text-sm text-gray-100">
              {requestPreview(definition)}
            </pre>
          </div>

          <dl className="grid grid-cols-3 gap-x-6 text-sm">
            <div>
              <dt className="text-foreground/60">Timeout</dt>
              <dd>{definition.timeoutSeconds ? `${definition.timeoutSeconds}s` : "None"}</dd>
            </div>
            <div>
              <dt className="text-foreground/60">Retries</dt>
              <dd>{retryLabel(definition.retryPolicy)}</dd>
            </div>
            <div>
              <dt className="text-foreground/60">Schedule</dt>
              <dd>Manual only</dd>
            </div>
          </dl>

          <DemoRunner id={content.id} />
        </section>
      ))}
    </div>
  );
}
