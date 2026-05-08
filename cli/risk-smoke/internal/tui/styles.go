package tui

import "github.com/charmbracelet/lipgloss"

// Real-Time Risk Lab palette.
var (
	colorOrange  = lipgloss.Color("#FF6B00")
	colorGreen   = lipgloss.Color("#00C853")
	colorRed     = lipgloss.Color("#D50000")
	colorGray    = lipgloss.Color("#9E9E9E")
	colorYellow  = lipgloss.Color("#FFD600")
	colorBg      = lipgloss.Color("#1A1A1A")
	colorText    = lipgloss.Color("#F5F5F5")
	colorSubtext = lipgloss.Color("#BDBDBD")
	colorBorder  = lipgloss.Color("#424242")
)

var (
	StyleHeader = lipgloss.NewStyle().
			Bold(true).
			Foreground(colorOrange).
			Background(colorBg).
			Padding(0, 2).
			MarginBottom(1)

	StyleStatusOK = lipgloss.NewStyle().
			Bold(true).
			Foreground(colorGreen)

	StyleStatusFail = lipgloss.NewStyle().
			Bold(true).
			Foreground(colorRed)

	StyleStatusRunning = lipgloss.NewStyle().
				Bold(true).
				Foreground(colorYellow)

	StyleStatusPending = lipgloss.NewStyle().
				Foreground(colorGray)

	StyleStatusSkipped = lipgloss.NewStyle().
				Foreground(colorGray).
				Italic(true)

	StyleSelected = lipgloss.NewStyle().
			Foreground(colorOrange).
			Bold(true)

	StyleNormal = lipgloss.NewStyle().
			Foreground(colorText)

	StyleSubtext = lipgloss.NewStyle().
			Foreground(colorSubtext)

	StyleDetail = lipgloss.NewStyle().
			Border(lipgloss.RoundedBorder()).
			BorderForeground(colorBorder).
			Padding(1, 2).
			Foreground(colorText)

	StyleFooter = lipgloss.NewStyle().
			Foreground(colorSubtext).
			MarginTop(1)

	StyleLabel = lipgloss.NewStyle().
			Foreground(colorOrange).
			Bold(true)

	StyleLatency = lipgloss.NewStyle().
			Foreground(colorYellow)

	StyleError = lipgloss.NewStyle().
			Foreground(colorRed)
)
