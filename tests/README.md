# Tests

Sample code files with known mistakes, plus the result we expect for each.

## Layout
```
tests/
└── samples/
    ├── add_wrong_operator.py             code with a known bug
    └── add_wrong_operator.expected.json  what the platform should report
```

## How to add a test
1. Write a small code file with one or two known mistakes in `tests/samples/`.
2. Write the expected issues next to it as `<name>.expected.json`.
3. Run the file through the platform and compare the actual report with the expected one.
4. If they do not match, log a bug, fix it and test again.
