# Development reconstruction retained scope — 2026-11-07

An accepted remaining-Step insertion could fail during ordinary continuation:
changing the Plan revision retained the current repair scope even when no repair
had occurred. The retained scope then prevented selecting the inserted Step.

The controller now retains the scope when the current boundary has actual repair
activity or a reservation. An ordinary boundary without that activity may move;
the accepted prefix, accounting and history remain in place. Assessment uses
the same boundary protection as application and also marks any amendment of a
pending or stopped reservation incompatible. Such amendments are not offered for
adoption. Existing repair convergence and reservation guards remain in force.

Controller and selection regression specifications cover an approved insertion,
a charged repair boundary, and a pending reservation including a same-Step
revision. Compile, focused and full validation, independent review, commit,
local publication, installed qualification and the original Studio Session
continuation are intended follow-up work and had not run when this entry was
written.
