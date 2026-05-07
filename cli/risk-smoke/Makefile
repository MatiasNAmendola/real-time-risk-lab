BIN      := bin/risk-smoke
PACKAGE  := ./...

.PHONY: build run headless tidy test clean

build:
	mkdir -p bin
	go build -o $(BIN) .

run: build
	./$(BIN)

headless: build
	./$(BIN) --headless

tidy:
	go mod tidy

test:
	go test $(PACKAGE)

clean:
	rm -rf bin/
