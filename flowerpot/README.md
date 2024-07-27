# The `flowerpot` ruleset format

> Keeping beautiful things organized.

## Basic overview

We want to be able to provide an automatic categorization of apps into folders and tabs and needed a fitting file format for storing rules in a compact but human-readable way. This is what flowerpot is, a simple format for rule lists which is easy to read and equally easy to parse from code. Repurposing the general ideas of this format for other uses is also easily possible.

## Format

Flowerpot files are built as a line delimited list of rules. Except for metadata related rules/tags, most rules are filters. The parser identifies rules by the leading character.

### Supported rules

| Identifier | Rule             | Description                         | Notes                                                   |
|:----------:|------------------|-------------------------------------|---------------------------------------------------------|
|            | Package          | Package name filter                 | Version 1 only supports full package names as filter    |
|     `#`    | Comment          | Comment                             |                                                         |
|     `$`    | Version          | Version tag (integer)               | Must be the first non-comment item, can only occur once |
|     `:`    | IntentAction     | Intent action to filter by          |                                                         |
|     `;`    | IntentCategory   | Intent category to filter by        | Tested in combination with the `MAIN` action            |
|     `&`    | CodeRule         | Rule which has been defined in code | Accepts arguments seperated by `|`                      |

### File names

Each file is a ruleset for one category, and the name of the file represents the name (codename) of that category. Flowerpot files have no file extension. You are advised to use uppercase (snake case) filenames spelling out the English name of the category.

### Versioning

Versions are identified by an incrementing integer value and accompanied by codenames. Codenames are given alphabetically and should be the name of a flower (I encourage everyone to use names of pink flowers, if possible).

#### Current Version

The format is currently at version 1 (`azalea`). This is the initial version of flowerpot.

> Azalea - A beautiful pink flower, known in China as "thinking of home bush" (sixiang shu).

#### Past versions

There are no past versions of flowerpot yet.