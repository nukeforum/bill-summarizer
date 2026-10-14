import ICDesign
import ICFeatures
import ICModels
import SwiftUI

struct CalendarView: View {
  @Bindable var model: CalendarFeatureModel

  var body: some View {
    NavigationStack {
      content
        .navigationTitle("Congress Calendar")
        .navigationDestination(for: Member.self) { member in
          MemberDetailView(model: model.detailModel(for: member))
        }
    }
    .task {
      guard case .loading = model.state else { return }
      await model.load()
    }
  }

  @ViewBuilder
  private var content: some View {
    switch model.state {
    case .loading:
      ProgressView("Loading calendar…")
        .frame(maxWidth: .infinity, maxHeight: .infinity)

    case .failed(let message):
      ContentUnavailableView {
        Label("Couldn’t Load Calendar", systemImage: "calendar.badge.exclamationmark")
      } description: {
        Text(message)
      } actions: {
        Button("Try Again") { Task { await model.load() } }
      }

    case .loaded(let snapshot):
      CalendarContent(snapshot: snapshot, today: model.today)
        .refreshable { await model.load() }
    }
  }
}

private struct CalendarContent: View {
  let snapshot: CalendarFeatureSnapshot
  let today: Date

  private var session: SessionCalendar { snapshot.calendar.session }
  private var elections: ElectionCalendar { snapshot.calendar.elections }

  var body: some View {
    ScrollView {
      LazyVStack(alignment: .leading, spacing: 20) {
        TodayCard(
          today: today,
          statuses: CalendarPresentation.statuses(in: session, on: today)
        )
        UpcomingElectionsCard(
          calendar: elections,
          stateCode: snapshot.stateCode,
          today: today
        )

        let ballot = CalendarPresentation.ballotRepresentatives(
          snapshot.savedRepresentatives,
          elections: elections,
          today: today
        )
        if !ballot.isEmpty {
          BallotCard(representatives: ballot)
        }

        SessionCalendarSection(calendar: session, today: today)
        ComingUpCard(calendar: session, today: today)
        SourcesFooter(source: session.source)
      }
      .padding()
    }
    .background(Color(.systemGroupedBackground))
    .accessibilityIdentifier("calendar-content")
  }
}

private struct TodayCard: View {
  let today: Date
  let statuses: [ChamberSessionStatus]

  var body: some View {
    CalendarCard {
      Text(today.formattedFullDate)
        .font(.headline)
      ForEach(statuses, id: \.chamber) { status in
        Label(
          status.line,
          systemImage: status.inSessionToday ? "checkmark.circle.fill" : "pause.circle"
        )
        .foregroundStyle(status.inSessionToday ? ICDesign.accent : .secondary)
      }
    }
    .accessibilityIdentifier("calendar-today")
  }
}

private struct UpcomingElectionsCard: View {
  let calendar: ElectionCalendar
  let stateCode: String?
  let today: Date

  private var elections: [UpcomingElection] {
    CalendarPresentation.upcomingElections(
      in: calendar,
      today: today,
      stateCode: stateCode
    )
  }

  var body: some View {
    CalendarCard {
      Label("Upcoming elections", systemImage: "checkmark.seal")
        .font(.headline)

      if elections.isEmpty {
        Text("No upcoming elections in the published calendar.")
          .foregroundStyle(.secondary)
      } else {
        ForEach(elections) { election in
          VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .firstTextBaseline) {
              VStack(alignment: .leading, spacing: 2) {
                Text(election.label)
                Text(election.date.formattedFullDate)
                  .font(.subheadline)
                  .foregroundStyle(.secondary)
              }
              Spacer()
              Text(election.countdown)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(ICDesign.accent)
            }

            if let deadline = CalendarPresentation.registrationDeadline(
              election.event.registration,
              today: today
            ) {
              RegistrationDeadlineView(deadline: deadline)
            }
          }
          .accessibilityElement(children: .combine)
          .accessibilityIdentifier("calendar-election-\(election.event.id)")
        }
        Text(calendar.source)
          .font(.caption)
          .foregroundStyle(.secondary)
      }

      Link(destination: CalendarPresentation.voteGovURL(stateCode: stateCode)) {
        Label("Register & vote", systemImage: "person.badge.key")
          .frame(maxWidth: .infinity)
      }
      .buttonStyle(.borderedProminent)
      .accessibilityIdentifier("calendar-register-vote")
      Text("Opens vote.gov inside the app")
        .font(.caption)
        .foregroundStyle(.secondary)
        .frame(maxWidth: .infinity)
    }
  }
}

private struct RegistrationDeadlineView: View {
  let deadline: RegistrationDeadlinePresentation
  @State private var showsAllDeadlines = false

  var body: some View {
    VStack(alignment: .leading, spacing: 4) {
      Label(
        deadline.headline,
        systemImage: deadline.isUrgent ? "exclamationmark.triangle" : "checkmark.seal"
      )
      .font(.caption)
      .foregroundStyle(deadline.isUrgent ? .red : .secondary)
      if let partial = deadline.partialClosedText {
        Text(partial).font(.caption).foregroundStyle(.secondary)
      }
      if deadline.sameDay {
        Text("You can still register in person at the polls (same-day registration).")
          .font(.caption)
          .foregroundStyle(.secondary)
      }
      if deadline.methods.count > 1 {
        Button(showsAllDeadlines ? "Hide deadlines" : "Show all deadlines") {
          showsAllDeadlines.toggle()
        }
        .font(.caption)
        if showsAllDeadlines {
          ForEach(deadline.methods) { method in
            Text("\(method.method.rawValue): \(method.date.formattedMonthDay)")
              .font(.caption)
              .foregroundStyle(.secondary)
          }
        }
      }
    }
  }
}

private struct BallotCard: View {
  let representatives: [BallotRepresentative]

  var body: some View {
    CalendarCard {
      Label("On your ballot", systemImage: "person.2")
        .font(.headline)
      ForEach(representatives) { representative in
        NavigationLink(value: representative.member) {
          HStack {
            VStack(alignment: .leading, spacing: 2) {
              Text(representative.member.name)
              Text(memberSubtitle(representative.member))
                .font(.caption)
                .foregroundStyle(.secondary)
              Text(representative.election.date.formattedFullDate)
                .font(.caption)
                .foregroundStyle(.secondary)
            }
            Spacer()
            Text(representative.election.countdown)
              .font(.caption.weight(.semibold))
              .foregroundStyle(ICDesign.accent)
          }
        }
      }
    }
  }

  private func memberSubtitle(_ member: Member) -> String {
    let chamber = member.chamber == .senate ? "Senator" : "Representative"
    return "\(chamber) · \(member.party)-\(member.state)"
  }
}

private struct SessionCalendarSection: View {
  let calendar: SessionCalendar
  let today: Date
  @State private var selectedDate: Date?

  private var months: [Date] {
    let foundationCalendar = Foundation.Calendar.current
    let start =
      foundationCalendar.date(
        from: foundationCalendar.dateComponents([.year, .month], from: today)
      ) ?? today
    return [
      start,
      foundationCalendar.date(byAdding: .month, value: 1, to: start) ?? start,
    ]
  }

  var body: some View {
    VStack(alignment: .leading, spacing: 16) {
      HStack(spacing: 16) {
        Legend(color: ICDesign.accent, text: "House")
        Legend(color: .purple, text: "Senate")
      }
      ForEach(months, id: \.self) { month in
        MonthGrid(
          month: month,
          today: today,
          selectedDate: selectedDate ?? today,
          houseDays: sessionDates(for: "house"),
          senateDays: sessionDates(for: "senate"),
          onSelect: { selectedDate = $0 }
        )
      }
      SelectedDayLine(
        date: selectedDate ?? today,
        houseDays: sessionDates(for: "house"),
        senateDays: sessionDates(for: "senate")
      )
    }
    .accessibilityIdentifier("calendar-months")
  }

  private func sessionDates(for chamber: String) -> Set<Date> {
    Set(calendar.chambers[chamber]?.sessionDays.compactMap(CalendarPresentation.date(from:)) ?? [])
  }
}

private struct Legend: View {
  let color: Color
  let text: String

  var body: some View {
    Label {
      Text(text).font(.caption.weight(.semibold))
    } icon: {
      RoundedRectangle(cornerRadius: 2).fill(color).frame(width: 18, height: 5)
    }
  }
}

private struct MonthGrid: View {
  let month: Date
  let today: Date
  let selectedDate: Date
  let houseDays: Set<Date>
  let senateDays: Set<Date>
  let onSelect: (Date) -> Void

  private let columns = Array(repeating: GridItem(.flexible(), spacing: 4), count: 7)
  private let weekdaySymbols = ["S", "M", "T", "W", "T", "F", "S"]

  var body: some View {
    VStack(alignment: .leading, spacing: 8) {
      Text(month.formatted(.dateTime.month(.wide).year()))
        .font(.headline)
      LazyVGrid(columns: columns, spacing: 4) {
        ForEach(Array(weekdaySymbols.enumerated()), id: \.offset) { _, symbol in
          Text(symbol)
            .font(.caption2.weight(.semibold))
            .frame(maxWidth: .infinity)
        }
        ForEach(Array(days.enumerated()), id: \.offset) { _, date in
          if let date {
            DayCell(
              date: date,
              isToday: Foundation.Calendar.current.isDate(date, inSameDayAs: today),
              isSelected: Foundation.Calendar.current.isDate(date, inSameDayAs: selectedDate),
              inHouse: houseDays.contains(date),
              inSenate: senateDays.contains(date),
              onSelect: onSelect
            )
          } else {
            Color.clear.frame(height: 48)
          }
        }
      }
    }
  }

  private var days: [Date?] {
    let calendar = Foundation.Calendar.current
    guard
      let range = calendar.range(of: .day, in: .month, for: month),
      let first = calendar.date(from: calendar.dateComponents([.year, .month], from: month))
    else { return [] }
    let leading = calendar.component(.weekday, from: first) - 1
    return Array(repeating: nil, count: leading)
      + range.compactMap { day in
        calendar.date(bySetting: .day, value: day, of: first)
      }.map(Optional.some)
  }
}

private struct DayCell: View {
  let date: Date
  let isToday: Bool
  let isSelected: Bool
  let inHouse: Bool
  let inSenate: Bool
  let onSelect: (Date) -> Void

  var body: some View {
    Button {
      onSelect(date)
    } label: {
      VStack(spacing: 3) {
        Text(date.formatted(.dateTime.day()))
          .font(.caption)
          .frame(width: 24, height: 24)
          .background(isToday ? ICDesign.accent.opacity(0.14) : .clear, in: Circle())
        RoundedRectangle(cornerRadius: 1.5)
          .fill(inHouse ? ICDesign.accent : .clear)
          .frame(height: 3)
        RoundedRectangle(cornerRadius: 1.5)
          .fill(inSenate ? Color.purple : .clear)
          .frame(height: 3)
      }
      .padding(3)
      .frame(maxWidth: .infinity, minHeight: 48)
      .overlay {
        RoundedRectangle(cornerRadius: 7)
          .stroke(
            isSelected ? ICDesign.accent : Color.secondary.opacity(0.2),
            lineWidth: isSelected ? 2 : 0.5)
      }
    }
    .buttonStyle(.plain)
    .accessibilityLabel(dayAccessibilityLabel)
  }

  private var dayAccessibilityLabel: String {
    let house = inHouse ? "House in session" : "House on recess"
    let senate = inSenate ? "Senate in session" : "Senate on recess"
    return "\(date.formattedFullDate), \(house), \(senate)"
  }
}

private struct SelectedDayLine: View {
  let date: Date
  let houseDays: Set<Date>
  let senateDays: Set<Date>

  var body: some View {
    Text(
      "\(date.formattedShortWeekday): \(houseDays.contains(date) ? "House in session" : "House on recess"), \(senateDays.contains(date) ? "Senate in session" : "Senate on recess")."
    )
    .font(.subheadline)
  }
}

private struct ComingUpCard: View {
  let calendar: SessionCalendar
  let today: Date

  var body: some View {
    CalendarCard {
      Label("Coming up", systemImage: "clock")
        .font(.headline)
      HStack(alignment: .top, spacing: 20) {
        ChamberBlocks(
          title: "House",
          blocks: CalendarPresentation.sessionBlocks(
            from: calendar.chambers["house"]?.sessionDays ?? [],
            today: today
          )
        )
        ChamberBlocks(
          title: "Senate",
          blocks: CalendarPresentation.sessionBlocks(
            from: calendar.chambers["senate"]?.sessionDays ?? [],
            today: today
          )
        )
      }
    }
  }
}

private struct ChamberBlocks: View {
  let title: String
  let blocks: [SessionBlock]

  var body: some View {
    VStack(alignment: .leading, spacing: 4) {
      Text(title).font(.subheadline.weight(.semibold))
      if blocks.isEmpty {
        Text("No scheduled session days")
          .font(.caption)
          .foregroundStyle(.secondary)
      } else {
        ForEach(blocks) { block in
          Text(block.rangeText).font(.caption)
        }
      }
    }
    .frame(maxWidth: .infinity, alignment: .leading)
  }
}

private struct SourcesFooter: View {
  let source: SessionCalendarSource

  var body: some View {
    VStack(alignment: .leading, spacing: 8) {
      Text("Calendar published by the Office of the House Majority Leader and the U.S. Senate.")
        .font(.caption)
        .foregroundStyle(.secondary)
      Link("House calendar", destination: source.house)
      Link("Senate calendar", destination: source.senate)
    }
  }
}

private struct CalendarCard<Content: View>: View {
  @ViewBuilder let content: Content

  var body: some View {
    VStack(alignment: .leading, spacing: 12) {
      content
    }
    .padding()
    .frame(maxWidth: .infinity, alignment: .leading)
    .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16))
  }
}
