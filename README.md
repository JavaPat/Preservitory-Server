Preservitory-Server is a Java-based RPG game server inspired by Classic RuneScape. It aims to provide a clean, data-driven architecture that is easily scalable for content creation.

Current Features
Core Engine
Networking: Custom packet-based communication layer.
Player Management: Persistent player data with saving/loading.
World System: Management of tiles, regions, and entity positions.
Data-Driven Definitions: Uses a cache system (./cache/) for JSON-defined enemies, items, NPCs, objects, quests, shops, and spawns.
Gameplay Systems
Combat: Basic Player vs. NPC combat system.
Skills: Woodcutting is currently implemented.
Quest System: Framework for defining and tracking quest progress.
UI Framework: Base components for interfaces like login, shops, and inventory.
NPC Interactions:
Dialogue: Structured NPC conversation system.
Shops: Functionality for buying and selling items.
Spawns: System for populating the world with NPCs and items.
Administration & Tools
Commands: Server-side commands for debugging and management.
Moderation: Player management tools (kick/ban).
Chat Filtering: Utility for validating and filtering in-game chat.
The project is currently in Phase 1 (Refinement), focusing on core engine improvements such as entity ID management and global debug systems.
