package tui

import (
	"github.com/charmbracelet/bubbles/spinner"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/naranjax/risk-smoke/internal/flows"
)

// Update is the Elm-style update function.
func (m Model) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {

	case tea.WindowSizeMsg:
		m.width = msg.Width
		m.height = msg.Height
		return m, nil

	case tea.KeyMsg:
		switch msg.String() {
		case "q", "ctrl+c":
			m.quitting = true
			return m, tea.Quit
		case "up", "k":
			if m.cursor > 0 {
				m.cursor--
			}
		case "down", "j":
			if m.cursor < len(m.checks)-1 {
				m.cursor++
			}
		case "r", "enter":
			return m, m.runCheck(m.checks[m.cursor].ID)
		case "a":
			return m, m.runAll()
		case "s":
			m.checks[m.cursor].Status = StatusSkipped
			m.checks[m.cursor].Detail = "Skipped by user"
		case "tab":
			e := m.checks[m.cursor]
			e.Tab = (e.Tab + 1) % 3
		}
		return m, nil

	case CheckResult:
		for _, e := range m.checks {
			if e.ID == msg.ID {
				e.Status = msg.Status
				e.Detail = msg.Detail
				e.Request = msg.Request
				e.Response = msg.Response
				e.ErrMsg = msg.ErrMsg
				e.Latency = msg.Latency
			}
		}
		return m, nil

	case RunCheckMsg:
		return m, m.runCheck(msg.ID)

	case RunAllMsg:
		return m, m.runAll()

	case spinner.TickMsg:
		var cmd tea.Cmd
		m.spinner, cmd = m.spinner.Update(msg)
		return m, cmd
	}

	return m, nil
}

// runCheck returns a command that executes a single check asynchronously.
func (m *Model) runCheck(id string) tea.Cmd {
	// Mark as running.
	for _, e := range m.checks {
		if e.ID == id {
			e.Status = StatusRunning
		}
	}
	cfg := m.cfg

	return func() tea.Msg {
		for _, chk := range flows.All(cfg) {
			if chk.ID() == id {
				r := chk.Run(cfg)
				status := StatusPassed
				if !r.Passed {
					if r.Skipped {
						status = StatusSkipped
					} else {
						status = StatusFailed
					}
				}
				return CheckResult{
					ID:       r.ID,
					Status:   status,
					Detail:   r.Detail,
					Request:  r.Request,
					Response: r.Response,
					ErrMsg:   r.ErrMsg,
					Latency:  r.Latency,
				}
			}
		}
		return CheckResult{ID: id, Status: StatusSkipped, Detail: "check not found"}
	}
}

// runAll returns a batch of commands to run every check.
func (m *Model) runAll() tea.Cmd {
	cmds := make([]tea.Cmd, 0, len(m.checks))
	for _, e := range m.checks {
		if e.Status != StatusSkipped {
			e.Status = StatusRunning
		}
		id := e.ID
		cmds = append(cmds, m.runCheck(id))
	}
	return tea.Batch(cmds...)
}
