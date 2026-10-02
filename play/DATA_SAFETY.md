# Data safety form — suggested answers (review with your own counsel)

| Question | Answer |
|---|---|
| Collects data? | Yes — only when the user connects with a partner |
| Financial info (expenses, amounts, merchants) | Collected, **shared with the user's partner only**, required for sync; not used for ads |
| Personal info: name (first name entered by user) | Collected, shared with partner, required for sync |
| App activity / device IDs | A random member token (not a hardware ID) |
| SMS / messages | **Not collected.** Bank texts are parsed on-device; raw text never leaves the phone (Play build has no SMS permission) |
| Data shared with third parties | Spending summary + questions go to **Anthropic** to power the agent (service provider, only if the agent is used) |
| Encrypted in transit | Yes (HTTPS) |
| Users can request deletion | Yes — in app (*Delete our Nest data from the server*) and web `/delete` |
| Ads / analytics SDKs | None |
