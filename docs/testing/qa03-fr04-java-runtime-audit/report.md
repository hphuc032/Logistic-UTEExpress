# FR-04 Java runtime audit — original evidence preserved

Source `5089a8ed17e5f8486e44847a99b309ff13d0d3b8`, tree `89c3882d470f81466b902cd3a38d7f3f272c889b`; original publication `1cf656d2b0a8853f91ccd58bc794c9ab395d0e23`.

## Integrity

Original private archive location and all hashes are in audit-receipt.json. Archive SHA-256 `3b5e97348dc76ecb5c75ac4ae28a7e1211ce4da0837c8524f62f259629161d84` verified before/after read-only audit. CRC PASS; all182 artifact hashes PASS, including144 JUnit XML. Original manifest SHA-256 `6cf8f34852c64e627ba863ac0c38c4af718c41191d9713b5ad3deed3ab837142`. No original receipt/log/XML rewritten.

## Proven facts and limits

| Question | Evidence / conclusion |
|---|---|
| Bare java version command | Original java-version.private.log directly reports Oracle24.0.2. It is incorrect to label every Java subprocess21. |
| Wrapper version command | Original maven-version.private.log directly reports Maven3.9.11, Java21.0.2, runtime C:\Program Files\Java\jdk-21. |
| Full Maven parent | Original full log records RequireJavaVersion passed; pinned pom.xml requires [21,22), proving parent Java21 major. Exact parent process executable telemetry was not captured; exact patch/path is supported by same runner JAVA_HOME, wrapper version and launcher, not separate OS telemetry. |
| Surefire unit fork | All77 original XML,709 cases: java.version21.0.2; specification21; homeJDK21; Oracle; VM21.0.2+13-LTS-58; UTC. Original startup log corroborates Java21.0.2. |
| Failsafe integration fork | All67 original XML,1419 cases: same direct Java21.0.2/JDK21/UTC evidence; original integration startup log corroborates. |
| Original auth-preflight helper runtime | NOT VERIFIED: original helper receipt records UTC, not Java version. Do not infer Java21 merely from JAVA_HOME. |
| Original bare java executable path | NOT VERIFIED: original process path/parent PATH was not recorded. |

## Resolution discrepancy investigation

The archived runner sets JAVA_HOME to JDK21 and supplies a child PATH beginning with JDK21\bin, but launches the version probe as bare `java`. On Windows these are distinct from explicitly selecting an executable.
A separate current diagnostic (NOT FR04 rerun) with the same environment construction observed parent resolution `C:\Program Files\Common Files\Oracle\Java\javapath\java.EXE`, child-PATH resolution JDK21, and actual bare invocation java.home=JDK24. Explicit JDK21 executable reports21.0.2. Thus the resolution discrepancy is reproduced today, without assuming the historical executable path.
The installed Maven3.9.11 bin/mvn.cmd chooses `%JAVA_HOME%\bin\java.exe` when JAVA_HOME is set (lines48–56), explaining why wrapper invocation selects21 independently of bare resolution. Surefire/Failsafe configuration has UTC and no alternate JVM/toolchain override. Actual XML, not that default assumption, establishes the test JVMs.

## Finding and disposition requested

Runtime-reporting discrepancy remains explicit. Test runtime21 is VERIFIED; full Maven parent major21 VERIFIED; historical bare path and preflight helper exact runtime NOT VERIFIED. No business regression finding is inferred from this metadata mismatch. TD raw verification and HP acceptance remain required. Recommend absolute JDK21 executables and java.runtime metadata in future probes, but no source/runner correction or rerun is performed here.

## Private handoff

Archive stays local/private; NOT DELIVERED. HP/TD must select an existing group-approved authenticated restricted channel, confirm recipient identity and expiry/access restrictions. QD transfers original ZIP without alteration, sends expected hash separately through an authenticated message. TD checks ZIP SHA, manifest SHA, CRC, all182 hashes and144 XML runtime properties; then acknowledges package hash, UTC receipt time and review scope. If no approved private channel is available, transfer remains BLOCKED; never put ZIP/raw XML/logs/credentials into public PR or an open share link.

FR05/06/07 NOT RUN; no FR04 rerun. PR69 remains Draft. Publication update is audit documentation only, not new execution evidence. G01 human PENDING; QA03 not COMPLETE.
