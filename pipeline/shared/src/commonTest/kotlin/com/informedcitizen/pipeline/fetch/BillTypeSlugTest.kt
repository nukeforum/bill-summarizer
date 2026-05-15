package com.informedcitizen.pipeline.fetch

import kotlin.test.Test
import kotlin.test.assertEquals

class BillTypeSlugTest {
    @Test fun hr_maps_to_house_bill_url() {
        assertEquals(
            "https://www.congress.gov/bill/119th-congress/house-bill/1234",
            congressGovUrl(119, "hr", "1234"),
        )
    }

    @Test fun senate_bill_url() {
        assertEquals(
            "https://www.congress.gov/bill/119th-congress/senate-bill/567",
            congressGovUrl(119, "s", "567"),
        )
    }

    @Test fun hjres_maps_to_house_joint_resolution() {
        assertEquals(
            "https://www.congress.gov/bill/118th-congress/house-joint-resolution/8",
            congressGovUrl(118, "hjres", "8"),
        )
    }

    @Test fun sjres_maps_to_senate_joint_resolution() {
        assertEquals(
            "https://www.congress.gov/bill/118th-congress/senate-joint-resolution/8",
            congressGovUrl(118, "sjres", "8"),
        )
    }

    @Test fun unknown_bill_type_falls_through_to_raw() {
        // Python `.get(bill_type, bill_type)` — unknown types use the code itself.
        assertEquals(
            "https://www.congress.gov/bill/119th-congress/wat/9",
            congressGovUrl(119, "wat", "9"),
        )
    }
}
