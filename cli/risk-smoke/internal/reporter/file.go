package reporter

import (
	"bytes"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"text/template"
	"time"

	"github.com/naranjax/risk-smoke/internal/flows"
)

// FileReporter writes structured reports under outDir/runID/.
type FileReporter struct {
	outDir     string
	onlyFail   bool
	runID      string
	baseURL    string
	total      int
	current    int
	startAt    time.Time
	runDir     string
	checksDir  string
	fullLog    *os.File
	results    []fileCheckRecord
}

type fileCheckRecord struct {
	idx      int
	check    flows.Check
	result   flows.Result
}

// meta written to meta.json
type runMeta struct {
	RunID    string    `json:"run_id"`
	Host     string    `json:"host"`
	BaseURL  string    `json:"base_url"`
	StartedAt string   `json:"started_at"`
	FinishedAt string  `json:"finished_at"`
	ExitCode int       `json:"exit_code"`
	Total    int       `json:"total"`
	Pass     int       `json:"pass"`
	Fail     int       `json:"fail"`
	Skip     int       `json:"skip"`
}

// NewFileReporter creates a FileReporter that writes under outDir.
func NewFileReporter(outDir string, onlyFail bool) *FileReporter {
	return &FileReporter{outDir: outDir, onlyFail: onlyFail}
}

func (r *FileReporter) Start(runID string, total int, baseURL string) {
	r.runID = runID
	r.total = total
	r.baseURL = baseURL
	r.startAt = time.Now()

	r.runDir = filepath.Join(r.outDir, runID)
	r.checksDir = filepath.Join(r.runDir, "checks")
	_ = os.MkdirAll(r.checksDir, 0o755)

	var err error
	r.fullLog, err = os.Create(filepath.Join(r.runDir, "full.log"))
	if err != nil {
		r.fullLog = nil
	}
	r.writeLog(fmt.Sprintf("=== risk-smoke run %s ===\nbase-url: %s\ntotal: %d\n\n", runID, baseURL, total))
}

func (r *FileReporter) OnCheckStart(c flows.Check) {
	r.current++
	r.writeLog(fmt.Sprintf("[%d/%d] START %s\n", r.current, r.total, c.ID()))
}

func (r *FileReporter) OnCheckProgress(c flows.Check, msg string) {
	r.writeLog(fmt.Sprintf("[%d/%d] PROGRESS %s: %s\n", r.current, r.total, c.ID(), msg))
}

func (r *FileReporter) OnCheckEnd(c flows.Check, result flows.Result) {
	status := statusStr(result)
	r.writeLog(fmt.Sprintf("[%d/%d] END %s  %s  (%dms)\n", r.current, r.total, c.ID(), status, result.Duration.Milliseconds()))
	for _, de := range result.Details {
		r.writeLog(fmt.Sprintf("  %s  %-6s  %s  %s\n", de.Timestamp.Format("15:04:05.000"), de.Status, de.Step, de.Note))
	}

	r.results = append(r.results, fileCheckRecord{
		idx:    r.current,
		check:  c,
		result: result,
	})

	// write individual check files unless onlyFail and this passed
	if r.onlyFail && result.Passed && !result.Skipped {
		return
	}
	r.writeCheckFiles(r.results[len(r.results)-1])
}

func (r *FileReporter) Finish(exitCode int) string {
	if r.runDir == "" {
		return ""
	}
	finishedAt := time.Now()

	pass, fail, skip := countResults(r.results)
	host, _ := os.Hostname()

	// meta.json
	meta := runMeta{
		RunID:      r.runID,
		Host:       host,
		BaseURL:    r.baseURL,
		StartedAt:  r.startAt.UTC().Format(time.RFC3339),
		FinishedAt: finishedAt.UTC().Format(time.RFC3339),
		ExitCode:   exitCode,
		Total:      r.total,
		Pass:       pass,
		Fail:       fail,
		Skip:       skip,
	}
	metaJSON, _ := json.MarshalIndent(meta, "", "  ")
	_ = os.WriteFile(filepath.Join(r.runDir, "meta.json"), metaJSON, 0o644)

	// summary.md
	summaryMD := r.renderSummaryMD(meta, finishedAt)
	_ = os.WriteFile(filepath.Join(r.runDir, "summary.md"), []byte(summaryMD), 0o644)

	// summary.txt
	summaryTXT := r.renderSummaryTXT(meta, finishedAt)
	_ = os.WriteFile(filepath.Join(r.runDir, "summary.txt"), []byte(summaryTXT), 0o644)

	if r.fullLog != nil {
		r.writeLog(fmt.Sprintf("\n=== DONE: exit=%d pass=%d fail=%d skip=%d ===\n", exitCode, pass, fail, skip))
		_ = r.fullLog.Close()
	}

	// symlink latest
	latestLink := filepath.Join(r.outDir, "latest")
	_ = os.Remove(latestLink)
	_ = os.Symlink(r.runID, latestLink)

	return r.runDir
}

// ─── internal helpers ────────────────────────────────────────────────────────

var summaryMDTmpl = template.Must(template.New("summary").Parse(`# Smoke run — {{.RunID}}

**Base URL**: {{.BaseURL}}
**Total**: {{.Total}} checks · **PASS**: {{.Pass}} · **FAIL**: {{.Fail}} · **SKIP**: {{.Skip}}
**Duration**: {{.Duration}}
**Exit code**: {{.ExitCode}}

| # | Check | Status | Duration | Detail |
|---|---|---|---|---|
{{- range .Rows}}
| {{.Idx}} | {{.ID}} | {{.Status}} | {{.Duration}} | [→](checks/{{.File}}) |
{{- end}}
`))

type summaryRow struct {
	Idx      int
	ID       string
	Status   string
	Duration string
	File     string
}

type summaryData struct {
	RunID    string
	BaseURL  string
	Total    int
	Pass     int
	Fail     int
	Skip     int
	Duration string
	ExitCode int
	Rows     []summaryRow
}

func (r *FileReporter) renderSummaryMD(meta runMeta, finishedAt time.Time) string {
	dur := finishedAt.Sub(r.startAt)
	data := summaryData{
		RunID:    meta.RunID,
		BaseURL:  meta.BaseURL,
		Total:    meta.Total,
		Pass:     meta.Pass,
		Fail:     meta.Fail,
		Skip:     meta.Skip,
		Duration: fmtDuration(dur),
		ExitCode: meta.ExitCode,
	}
	for _, rec := range r.results {
		data.Rows = append(data.Rows, summaryRow{
			Idx:      rec.idx,
			ID:       rec.check.ID(),
			Status:   statusStr(rec.result),
			Duration: fmtDuration(rec.result.Duration),
			File:     fmt.Sprintf("%02d-%s.md", rec.idx, rec.check.ID()),
		})
	}
	var buf bytes.Buffer
	_ = summaryMDTmpl.Execute(&buf, data)
	return buf.String()
}

func (r *FileReporter) renderSummaryTXT(meta runMeta, finishedAt time.Time) string {
	dur := finishedAt.Sub(r.startAt)
	var sb strings.Builder
	sb.WriteString(fmt.Sprintf("Smoke run: %s\n", meta.RunID))
	sb.WriteString(fmt.Sprintf("Base URL:  %s\n", meta.BaseURL))
	sb.WriteString(fmt.Sprintf("Total: %d  PASS: %d  FAIL: %d  SKIP: %d\n", meta.Total, meta.Pass, meta.Fail, meta.Skip))
	sb.WriteString(fmt.Sprintf("Duration: %s  Exit: %d\n", fmtDuration(dur), meta.ExitCode))
	sb.WriteString(strings.Repeat("-", 60) + "\n")
	for _, rec := range r.results {
		sb.WriteString(fmt.Sprintf("%-3d  %-12s  %-4s  %s\n",
			rec.idx, rec.check.ID(), statusStr(rec.result), fmtDuration(rec.result.Duration)))
	}
	return sb.String()
}

type checkMDData struct {
	Name      string
	IDPadded  string
	Status    string
	StartedAt string
	Duration  string
	Details   []flows.DetailEntry
	Artifacts map[string]string
}

func (r *FileReporter) writeCheckFiles(rec fileCheckRecord) {
	base := fmt.Sprintf("%02d-%s", rec.idx, rec.check.ID())

	// raw log
	var logBuf strings.Builder
	logBuf.WriteString(fmt.Sprintf("=== %s (%s) ===\n", rec.check.ID(), statusStr(rec.result)))
	logBuf.WriteString(fmt.Sprintf("Duration: %dms\n\n", rec.result.Duration.Milliseconds()))
	for _, de := range rec.result.Details {
		logBuf.WriteString(fmt.Sprintf("[%s] %s  %s  %s\n", de.Timestamp.Format("15:04:05.000"), de.Status, de.Step, de.Note))
		if de.Payload != "" {
			logBuf.WriteString("--- payload ---\n")
			logBuf.WriteString(de.Payload)
			logBuf.WriteString("\n--- end ---\n\n")
		}
	}
	_ = os.WriteFile(filepath.Join(r.checksDir, base+".log"), []byte(logBuf.String()), 0o644)

	// markdown
	data := checkMDData{
		Name:      rec.check.Name(),
		IDPadded:  fmt.Sprintf("%02d", rec.idx),
		Status:    statusStr(rec.result),
		StartedAt: rec.result.StartedAt.UTC().Format(time.RFC3339Nano),
		Duration:  fmtDuration(rec.result.Duration),
		Details:   rec.result.Details,
		Artifacts: rec.result.Artifacts,
	}

	// use a fresh template with funcs
	tmpl := template.Must(template.New("check").Funcs(template.FuncMap{
		"inc": func(i int) int { return i + 1 },
	}).Parse(`# Check: {{.Name}} (id {{.IDPadded}})

**Status**: {{.Status}}
**Started**: {{.StartedAt}}
**Duration**: {{.Duration}}

## Steps

| # | Time | Step | Status | Note |
|---|---|---|---|---|
{{- range $i, $d := .Details}}
| {{inc $i}} | {{$d.Timestamp.Format "15:04:05.000"}} | {{$d.Step}} | {{$d.Status}} | {{$d.Note}} |
{{- end}}
{{if .Artifacts}}
## Artifacts
{{range $k, $v := .Artifacts}}
### {{$k}}
` + "```" + `
{{$v}}
` + "```" + `
{{end}}
{{end}}
`))

	var mdBuf bytes.Buffer
	_ = tmpl.Execute(&mdBuf, data)
	_ = os.WriteFile(filepath.Join(r.checksDir, base+".md"), mdBuf.Bytes(), 0o644)
}

func (r *FileReporter) writeLog(msg string) {
	if r.fullLog != nil {
		_, _ = r.fullLog.WriteString(msg)
	}
}

func statusStr(result flows.Result) string {
	switch {
	case result.Skipped:
		return "SKIP"
	case result.Passed:
		return "PASS"
	default:
		return "FAIL"
	}
}

func fmtDuration(d time.Duration) string {
	if d < time.Second {
		return fmt.Sprintf("%dms", d.Milliseconds())
	}
	return fmt.Sprintf("%.1fs", d.Seconds())
}

func countResults(recs []fileCheckRecord) (pass, fail, skip int) {
	for _, r := range recs {
		switch statusStr(r.result) {
		case "PASS":
			pass++
		case "FAIL":
			fail++
		case "SKIP":
			skip++
		}
	}
	return
}
