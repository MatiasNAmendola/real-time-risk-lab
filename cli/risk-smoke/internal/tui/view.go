package tui

import (
	"fmt"
	"strings"

	"github.com/charmbracelet/lipgloss"
)

// View renders the full TUI screen.
func (m Model) View() string {
	if m.quitting {
		return "Goodbye.\n"
	}

	var sb strings.Builder

	// Header
	globalStatus := m.globalStatus()
	sb.WriteString(StyleHeader.Render(
		fmt.Sprintf("  Risk Engine Smoke Runner  %s", globalStatus),
	))
	sb.WriteString("\n")

	// Check list
	for i, e := range m.checks {
		row := m.renderCheckRow(i, e)
		sb.WriteString(row)
		sb.WriteString("\n")
	}

	sb.WriteString("\n")

	// Detail panel for selected check
	if m.cursor >= 0 && m.cursor < len(m.checks) {
		sb.WriteString(m.renderDetail(m.checks[m.cursor]))
		sb.WriteString("\n")
	}

	// Footer
	sb.WriteString(StyleFooter.Render(
		"↑/↓ navigate  enter/r re-run  a run-all  s skip  tab detail-tab  q quit",
	))

	return sb.String()
}

func (m Model) renderCheckRow(idx int, e *CheckEntry) string {
	icon := e.Status.Icon()
	if e.Status == StatusRunning {
		icon = m.spinner.View()
	}

	name := e.Name
	if idx == m.cursor {
		name = StyleSelected.Render("> " + name)
	} else {
		name = StyleNormal.Render("  " + name)
	}

	statusStr := styleStatus(e.Status, icon+" "+e.Status.String())
	latency := ""
	if e.Latency != "" {
		latency = StyleLatency.Render("  " + e.Latency)
	}

	return lipgloss.JoinHorizontal(lipgloss.Top,
		fmt.Sprintf("%-50s", name),
		statusStr,
		latency,
	)
}

func (m Model) renderDetail(e *CheckEntry) string {
	tabs := []string{"[detail]", "[request]", "[response/error]"}
	tabBar := make([]string, len(tabs))
	for i, t := range tabs {
		if i == e.Tab {
			tabBar[i] = StyleLabel.Render(t)
		} else {
			tabBar[i] = StyleSubtext.Render(t)
		}
	}

	var content string
	switch e.Tab {
	case 0:
		content = e.Detail
		if e.ErrMsg != "" {
			content += "\n" + StyleError.Render("Error: "+e.ErrMsg)
		}
		if e.Latency != "" {
			content += "\n" + StyleLatency.Render("Latency: "+e.Latency)
		}
	case 1:
		content = e.Request
	case 2:
		if e.ErrMsg != "" {
			content = StyleError.Render(e.ErrMsg)
			if e.Response != "" {
				content += "\n\n" + e.Response
			}
		} else {
			content = e.Response
		}
	}

	if content == "" {
		content = StyleSubtext.Render("(no data yet)")
	}

	header := strings.Join(tabBar, "  ")
	return StyleDetail.Render(header + "\n\n" + content)
}

func (m Model) globalStatus() string {
	pass, fail, run, pending := 0, 0, 0, 0
	for _, e := range m.checks {
		switch e.Status {
		case StatusPassed:
			pass++
		case StatusFailed:
			fail++
		case StatusRunning:
			run++
		default:
			pending++
		}
	}

	if run > 0 {
		return StyleStatusRunning.Render(fmt.Sprintf("● RUNNING (%d/%d)", pass, len(m.checks)))
	}
	if fail > 0 {
		return StyleStatusFail.Render(fmt.Sprintf("✗ FAILED (%d pass / %d fail)", pass, fail))
	}
	if pending > 0 {
		return StyleStatusPending.Render(fmt.Sprintf("○ PENDING (%d/%d done)", pass, len(m.checks)))
	}
	return StyleStatusOK.Render(fmt.Sprintf("✓ ALL PASSED (%d/%d)", pass, len(m.checks)))
}

func styleStatus(s CheckStatus, text string) string {
	switch s {
	case StatusPassed:
		return StyleStatusOK.Render(text)
	case StatusFailed:
		return StyleStatusFail.Render(text)
	case StatusRunning:
		return StyleStatusRunning.Render(text)
	case StatusSkipped:
		return StyleStatusSkipped.Render(text)
	default:
		return StyleStatusPending.Render(text)
	}
}
