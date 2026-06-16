import SwiftUI

struct Message: Identifiable {
    let id = UUID()
    let text: String
    let isUser: Bool
}

class ChatViewModel: ObservableObject {
    @Published var messages: [Message] = []
    @Published var isLoading = false
    @Published var status = "Model not loaded"

    private let bridge = LlmBridge()

    private var modelURL: URL {
        FileManager.default
            .urls(for: .documentDirectory, in: .userDomainMask)
            .first!
            .appendingPathComponent("gemma-4b-it.gguf")
    }

    func load() {
        Task {
            await MainActor.run { status = "Loading model..." }
            let ok = bridge.loadModel(modelURL.path)
            await MainActor.run { status = ok ? "Model loaded" : "Failed to load" }
        }
    }

    func send(_ text: String) {
        guard !text.isEmpty, !isLoading, status == "Model loaded" else { return }
        messages.append(Message(text: text, isUser: true))
        isLoading = true
        Task {
            let reply = bridge.generate(text)
            await MainActor.run {
                messages.append(Message(text: reply, isUser: false))
                isLoading = false
            }
        }
    }
}

struct ContentView: View {
    @StateObject private var viewModel = ChatViewModel()
    @State private var input = ""

    var body: some View {
        VStack(spacing: 12) {
            Text(viewModel.status)
                .font(.caption)
                .foregroundColor(.secondary)

            List(viewModel.messages) { message in
                HStack {
                    if message.isUser { Spacer() }
                    Text(message.text)
                        .padding(10)
                        .background(message.isUser ? Color.blue.opacity(0.2) : Color.gray.opacity(0.2))
                        .cornerRadius(10)
                    if !message.isUser { Spacer() }
                }
                .listRowSeparator(.hidden)
            }
            .listStyle(.plain)

            HStack {
                TextField("Type a prompt...", text: $input)
                    .textFieldStyle(.roundedBorder)
                Button("Send") {
                    viewModel.send(input)
                    input = ""
                }
                .disabled(viewModel.isLoading || input.isEmpty || viewModel.status != "Model loaded")
            }
            .padding()
        }
        .onAppear {
            viewModel.load()
        }
    }
}
