import sys
import io
import textwrap
import traceback


def run(code):
    code = textwrap.dedent(code).strip()
    captured_out = io.StringIO()
    captured_err = io.StringIO()
    original_stdout = sys.stdout
    original_stderr = sys.stderr
    exit_code = 0

    sys.stdout = captured_out
    sys.stderr = captured_err

    try:
        namespace = {"__name__": "__main__"}
        exec(compile(code, "<generated>", "exec"), namespace)
    except SystemExit as e:
        exit_code = e.code if isinstance(e.code, int) else (1 if e.code else 0)
    except Exception:
        traceback.print_exc()
        exit_code = 1
    finally:
        sys.stdout = original_stdout
        sys.stderr = original_stderr

    return [captured_out.getvalue(), captured_err.getvalue(), exit_code]
