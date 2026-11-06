export interface ProjectInfo {
  name: string;
  tagline: string;
  version: string;
  stage: string;
}

export async function getProjectInfo(
  signal?: AbortSignal,
): Promise<ProjectInfo> {
  const response = await fetch("/api/v1/info", {
    credentials: "same-origin",
    signal,
  });
  if (!response.ok) throw new Error(`API returned ${response.status}`);
  return response.json();
}
