# Course Planner

A desktop app for planning courses semester by semester. It reads the course
catalog from `catalog.json`, checks prerequisites as you plan, and flags
anything that's missing.

Written in plain Java with Swing. No outside libraries or build tools.

## Requirements

- Java 17 or newer (developed on Java 24). Check with `java -version`.

## Running

From the project folder:

```sh
./run.sh
```

This compiles the code into `out/` and opens the window. If you get
"permission denied", run `chmod +x run.sh` once, or use `sh run.sh`.

To compile and run by hand:

```sh
javac -d out src/planner/*.java
java -cp out planner.Main catalog.json
```

The last argument is the catalog file. It defaults to `catalog.json` in the
current folder.

## Using the planner

**Left side: the catalog**

- Type in **Search** to find courses by code (`math 2`, `chem103`) or by words
  in the title.
- Click a course to see its credits, terms offered, prerequisites,
  restrictions, description, and which courses list it as a prerequisite.
- Each result has a badge (hover for details):

  | Badge   | Meaning                                                  |
  |---------|----------------------------------------------------------|
  | Ready   | Courses already in your plan cover its prerequisites     |
  | Check   | It has prerequisites that can't be checked automatically |
  | Locked  | It needs a course that isn't in your plan yet            |
  | In plan | It's already in your plan                                |

**Right side: your plan**

- The first column, **Completed / Transfer**, holds courses you've already
  taken. They count toward prerequisites.
- Each other column is a semester showing its credit total.
- To add a course:
  - drag it from the search results onto a column, or
  - double-click it, or press **Add selected course to →**, which adds it to
    the column outlined in blue.
- Drag a card to another column to move it. You can also click a card and use
  **◀ / ▶** to move it, or **Remove** to take it out.
- **Add semester** suggests the next term name. Double-click a semester's
  title to rename it; **✕** deletes the semester.
- **Save plan… / Load plan…** store the plan as a small text file.

**Card colors**

| Stripe | Meaning                                                                                                                   |
|--------|---------------------------------------------------------------------------------------------------------------------------|
| Green  | Prerequisites are done in Completed or an earlier semester                                                                |
| Yellow | Check yourself: a prerequisite the app can't verify, the course is already in the plan, or it isn't usually offered that term |
| Red    | A required prerequisite is missing                                                                                        |
| Gray   | In the Completed column                                                                                                   |

The first problem is written on the card. Hover to see all of them.

## How it works

### The catalog

`catalog.json` groups courses by department:

```json
[
  {
    "ACCT": {
      "ACCT 208": {
        "id": "ACCT 208",
        "name": "Accounting II",
        "credits": " 3",
        "preReq": "ACCT 207.",
        "restrict": "Not open to freshmen. ...",
        "typ": "Fall, Winter, Spring, Summer",
        "descr": "..."
      }
    }
  }
]
```

`typ` lists the terms the course is normally offered. `credits` may be a range
such as `1-3`; semester totals then show a range too.

### Reading prerequisites

Prerequisites in the catalog are plain English, so `PrereqParser` turns them
into rules. Each course gets a list of **groups**. Every group must be
satisfied, and any one course in a group satisfies it.

| Text                                              | Read as                                       |
|---------------------------------------------------|-----------------------------------------------|
| `ACCT 207.`                                       | ACCT 207                                      |
| `ECON 101 and ECON 103`                           | ECON 101 **and** ECON 103                     |
| `CHEM322 or CHEM332`                              | CHEM 322 **or** CHEM 332                      |
| `CHEM103/104`                                     | CHEM 103 **or** CHEM 104                      |
| `MATH 241, 242, or 243`                           | any one of the three                          |
| `PSYC209 or approved substitutes (STAT200, MATH202)` | any one of PSYC 209, STAT 200, MATH 202    |
| `C- or better in NSCI100`                         | NSCI 100 (grade wording is ignored)           |
| `MATH 241 or permission of instructor`            | MATH 241, but a miss is only a warning        |
| `Any two 200-level German courses`                | a note to check yourself                      |

The rules:

- `;`, sentence breaks, `,` and `and` separate requirements, which all apply.
- `or` and `/` separate alternatives. A list ending in `, or X` means any one
  of the items.
- A bare number like `104` in `CHEM 103/104` reuses the last department code.
- Only codes that exist in the catalog count. Anything else becomes a note.
- If the text mentions "concurrent" or "corequisite", prerequisites taken in
  the same semester count.

About 90% of courses with prerequisites are checked automatically. The rest
show their original text as a note.

**Limitations:** grade requirements aren't enforced, and nested logic like
"(A and B) or C" is simplified, so some courses may be flagged a bit too
strictly or too loosely. The original prerequisite text is always shown in the
details panel.

### Checking a course

For a course in semester *N*, `Plan.check` looks at the courses in Completed
and in semesters before *N* (plus semester *N* itself if concurrent enrollment
is allowed), then reports:

- **Error**: a required group isn't satisfied.
- **Warning**: a group with a non-course alternative isn't satisfied, there's
  an unreadable note, the course is already in the plan, or the semester's
  term (the first word of its name: Fall, Winter, Spring or Summer) isn't in
  the course's `typ`.

### Plan files

Plans are saved as plain text, one line per column:

```
# Course plan
COMPLETED|ENGL 110
SEMESTER|Fall 2026|ACCT 207|MATH 241
SEMESTER|Spring 2027|ACCT 208
```

## Project layout

```
catalog.json              course data
run.sh                    compile and launch
src/planner/
  Main.java               entry point: loads the catalog, opens the window
  JsonParser.java         small JSON reader (no libraries)
  Course.java             one course: fields, credit range, terms offered
  Catalog.java            loads all courses, lookup, search, "prerequisite for" index
  Prereq.java             prerequisite groups and notes
  PrereqParser.java       turns prerequisite text into Prereq groups
  Plan.java               semesters, prerequisite checks, badges, save/load
  PlannerFrame.java       the Swing window: search, details, cards, drag and drop
```
