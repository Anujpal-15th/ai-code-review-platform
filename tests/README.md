# Tests

Coding-question solutions with known mistakes, plus the result we expect for each.

## Layout
```
tests/
└── samples/
    ├── two_sum_sorted_wrong_pointer.py             code with a known bug
    └── two_sum_sorted_wrong_pointer.expected.json  what the platform should report
```

## How to add a sample
1. Write a LeetCode-style `Solution` class with one known mistake in `tests/samples/`.
2. Write `<name>.expected.json` next to it, with the question, failing test, wrong output and the line of the mistake.
3. Run it yourself to confirm the wrong output is real.
4. Run it through the platform and compare the result with the expected file.
5. If they do not match, log a bug, fix it and test again.

Use real student mistakes when you can. They are a fairer test than bugs we invent ourselves.
