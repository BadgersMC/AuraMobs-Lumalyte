# AuraMobs-Lumalyte
An add-on for AuraSkills adding mob levels based on skills.

>>   Added support for advanced, nested player level formulas in config (exp4j)
>>   * Now supports deeply nested max(...) and min(...) expressions, allowing for complex skill-based scaling.
>>   * Example: Player level can now be calculated with weighted skills, specialization bonuses, and penalties for neglected skills.
>>   * Enables configs like:
>>     0.5*{archery} + ... + 0.15*max(...all skills...) - 0.2*min(...all skills...)
>>
>>   Implemented mob attraction to KingdomsX structures
>>   * Mobs are now attracted to invisible villagers placed at kingdom structures.
>>   * This allows for dynamic mob behavior around player-built areas.
>>
>>   Refactored MobAttractionManager for async pathfinding
>>   * Designed to work with Leaf's async pathfinder patch for improved server performance.
>>   * Ensures mob pathfinding is efficient and non-blocking.
>>
>>   Introduced Blood Moon event system
>>   * Special event where mobs spawn with unique armor and enchantments
>>   * Tracks and broadcasts mob kills, with configurable thresholds and event end conditions.
>>   * Supports spawning of special mobs (Chicken Jockeys, Pillagers, Vindicators, rare Illusioners).
>>
>>   General improvements and bugfixes
>>   * Working on refactoring deprecated spigot api methods to paper api
>>   * Improved code structure and configurability.
>>
BREAKING CHANGES: Some features require Leaf async pathfinding. Plugin also no longer targets java 17.

View the wiki [here](https://wiki.aurelium.dev/auramobs).
