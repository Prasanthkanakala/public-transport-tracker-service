UML Diagrams for Public Transport Tracker Service

This folder contains PlantUML diagrams generated from the actual backend source code.

Files:

- `architecture.puml` - backend service architecture and request flow components
- `sequence.puml` - request lifecycle with cache and fallback behavior
- `class.puml` - backend controller/service/client/model class relationships
- `deployment.puml` - deployment topology

How to render

- VS Code: install the "PlantUML" extension and open `.puml` files to preview and export images.
- CLI (requires Java and PlantUML JAR):
  - `java -jar plantuml.jar docs/uml/*.puml`

Notes

- The architecture and class diagrams reflect `com.transport.tracker` packages, including `TransportController`, `TransportService`, `CacheService`, `TransitApiClient`, and model classes.
- The sequence diagram captures the service resilience chain: `OFFLINE → CACHE → LIVE → STALE_CACHE → MOCK`.
