# Database model overview

The JPA entities in `src/main/java/com/campus/campus_backend/domain/` define the current model. This overview is descriptive; inspect the entities and migrations before changing a deployed database.

| Area | Main entities | Purpose |
| --- | --- | --- |
| Accounts | `User`, `UserFollow`, `PointTransaction` | Profiles, relationships, and points |
| Community | `Category`, `Post`, `Comment`, `PostLike`, `PostCollection` | Posts and interaction |
| Polls | `PostPoll`, `PostPollOption`, `PostPollVote` | Post voting |
| Errands | `Errand` | Campus errands |
| Messaging | `Conversation`, `ChatMessage`, `Notification` | Private messages and notifications |
| Information center | `SubscriptionSource`, `AggregatedNotice`, `NoticeSubscription`, `NoticeComment` | External and manual notices |
| Moderation | `Report`, `governance/*` | Reports and governance events |
| Files | `SysFile` | Upload metadata |

Passwords in the `User` model are BCrypt hashes. A Canvas binding may contain a separate Canvas password; treat that database and its backups as sensitive.
