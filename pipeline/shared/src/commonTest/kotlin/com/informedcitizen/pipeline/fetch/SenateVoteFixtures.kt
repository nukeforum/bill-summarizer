package com.informedcitizen.pipeline.fetch

/**
 * Real senate.gov LIS fixtures, byte-identical to the Python suite's
 * `data-pipeline/tests/fixtures/` copies (fetched 2026-07-21):
 * the full detail XML for roll call 119-1-618 (passage of H.R. 5371,
 * the FY26 continuing resolution), the session vote menu trimmed to
 * six representative entries, and the lis -> bioguide map for the 100
 * senators voting on roll call 618 (extracted from the union of
 * legislators-current.yaml and legislators-historical.yaml).
 */
internal val SENATE_VOTE_618_XML: String = """<?xml version="1.0" encoding="UTF-8"?><roll_call_vote>
  <congress>119</congress>
  <session>1</session>
  <congress_year>2025</congress_year>
  <vote_number>618</vote_number>
  <vote_date>November 10, 2025,  08:58 PM</vote_date>
  <modify_date>November 10, 2025,  09:36 PM</modify_date>
  <vote_question_text>On Passage of the Bill H.R. 5371</vote_question_text>
  <vote_document_text>A bill making continuing appropriations and extensions for fiscal year 2026, and for other purposes.</vote_document_text>
  <vote_result_text>Bill Passed (60-40)</vote_result_text>
  <question>On Passage of the Bill</question>
  <vote_title>H.R. 5371, As Amended</vote_title>
  <majority_requirement>1/2</majority_requirement>
  <vote_result>Bill Passed</vote_result>
  <document>
    <document_congress>119</document_congress>
    <document_type>H.R.</document_type>
    <document_number>5371</document_number>
    <document_name>H.R. 5371</document_name>
    <document_title>A bill making continuing appropriations and extensions for fiscal year 2026, and for other purposes.</document_title>
    <document_short_title>A bill making continuing appropriations and extensions for fiscal year 2026, and for other purposes.</document_short_title>
  </document>
  <amendment>
    <amendment_number/>
    <amendment_to_amendment_number/>
    <amendment_to_amendment_to_amendment_number/>
    <amendment_to_document_number/>
    <amendment_to_document_short_title/>
    <amendment_purpose>No Statement of Purpose on File.</amendment_purpose>
  </amendment>
  <count>
    <yeas>60</yeas>
    <nays>40</nays>
    <present/>
    <absent/>
  </count>
  <tie_breaker>
    <by_whom/>
    <tie_breaker_vote/>
  </tie_breaker>
  <members>
    <member>
      <member_full>Alsobrooks (D-MD)</member_full>
      <last_name>Alsobrooks</last_name>
      <first_name>Angela</first_name>
      <party>D</party>
      <state>MD</state>
      <vote_cast>Nay</vote_cast>
      <lis_member_id>S428</lis_member_id>
    </member>
    <member>
      <member_full>Baldwin (D-WI)</member_full>
      <last_name>Baldwin</last_name>
      <first_name>Tammy</first_name>
      <party>D</party>
      <state>WI</state>
      <vote_cast>Nay</vote_cast>
      <lis_member_id>S354</lis_member_id>
    </member>
    <member>
      <member_full>Banks (R-IN)</member_full>
      <last_name>Banks</last_name>
      <first_name>Jim</first_name>
      <party>R</party>
      <state>IN</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S429</lis_member_id>
    </member>
    <member>
      <member_full>Barrasso (R-WY)</member_full>
      <last_name>Barrasso</last_name>
      <first_name>John</first_name>
      <party>R</party>
      <state>WY</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S317</lis_member_id>
    </member>
    <member>
      <member_full>Bennet (D-CO)</member_full>
      <last_name>Bennet</last_name>
      <first_name>Michael</first_name>
      <party>D</party>
      <state>CO</state>
      <vote_cast>Nay</vote_cast>
      <lis_member_id>S330</lis_member_id>
    </member>
    <member>
      <member_full>Blackburn (R-TN)</member_full>
      <last_name>Blackburn</last_name>
      <first_name>Marsha</first_name>
      <party>R</party>
      <state>TN</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S396</lis_member_id>
    </member>
    <member>
      <member_full>Blumenthal (D-CT)</member_full>
      <last_name>Blumenthal</last_name>
      <first_name>Richard</first_name>
      <party>D</party>
      <state>CT</state>
      <vote_cast>Nay</vote_cast>
      <lis_member_id>S341</lis_member_id>
    </member>
    <member>
      <member_full>Blunt Rochester (D-DE)</member_full>
      <last_name>Blunt Rochester</last_name>
      <first_name>Lisa</first_name>
      <party>D</party>
      <state>DE</state>
      <vote_cast>Nay</vote_cast>
      <lis_member_id>S430</lis_member_id>
    </member>
    <member>
      <member_full>Booker (D-NJ)</member_full>
      <last_name>Booker</last_name>
      <first_name>Cory</first_name>
      <party>D</party>
      <state>NJ</state>
      <vote_cast>Nay</vote_cast>
      <lis_member_id>S370</lis_member_id>
    </member>
    <member>
      <member_full>Boozman (R-AR)</member_full>
      <last_name>Boozman</last_name>
      <first_name>John</first_name>
      <party>R</party>
      <state>AR</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S343</lis_member_id>
    </member>
    <member>
      <member_full>Britt (R-AL)</member_full>
      <last_name>Britt</last_name>
      <first_name>Katie</first_name>
      <party>R</party>
      <state>AL</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S416</lis_member_id>
    </member>
    <member>
      <member_full>Budd (R-NC)</member_full>
      <last_name>Budd</last_name>
      <first_name>Ted</first_name>
      <party>R</party>
      <state>NC</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S417</lis_member_id>
    </member>
    <member>
      <member_full>Cantwell (D-WA)</member_full>
      <last_name>Cantwell</last_name>
      <first_name>Maria</first_name>
      <party>D</party>
      <state>WA</state>
      <vote_cast>Nay</vote_cast>
      <lis_member_id>S275</lis_member_id>
    </member>
    <member>
      <member_full>Capito (R-WV)</member_full>
      <last_name>Capito</last_name>
      <first_name>Shelley</first_name>
      <party>R</party>
      <state>WV</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S372</lis_member_id>
    </member>
    <member>
      <member_full>Cassidy (R-LA)</member_full>
      <last_name>Cassidy</last_name>
      <first_name>Bill</first_name>
      <party>R</party>
      <state>LA</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S373</lis_member_id>
    </member>
    <member>
      <member_full>Collins (R-ME)</member_full>
      <last_name>Collins</last_name>
      <first_name>Susan</first_name>
      <party>R</party>
      <state>ME</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S252</lis_member_id>
    </member>
    <member>
      <member_full>Coons (D-DE)</member_full>
      <last_name>Coons</last_name>
      <first_name>Christopher</first_name>
      <party>D</party>
      <state>DE</state>
      <vote_cast>Nay</vote_cast>
      <lis_member_id>S337</lis_member_id>
    </member>
    <member>
      <member_full>Cornyn (R-TX)</member_full>
      <last_name>Cornyn</last_name>
      <first_name>John</first_name>
      <party>R</party>
      <state>TX</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S287</lis_member_id>
    </member>
    <member>
      <member_full>Cortez Masto (D-NV)</member_full>
      <last_name>Cortez Masto</last_name>
      <first_name>Catherine</first_name>
      <party>D</party>
      <state>NV</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S385</lis_member_id>
    </member>
    <member>
      <member_full>Cotton (R-AR)</member_full>
      <last_name>Cotton</last_name>
      <first_name>Tom</first_name>
      <party>R</party>
      <state>AR</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S374</lis_member_id>
    </member>
    <member>
      <member_full>Cramer (R-ND)</member_full>
      <last_name>Cramer</last_name>
      <first_name>Kevin</first_name>
      <party>R</party>
      <state>ND</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S398</lis_member_id>
    </member>
    <member>
      <member_full>Crapo (R-ID)</member_full>
      <last_name>Crapo</last_name>
      <first_name>Mike</first_name>
      <party>R</party>
      <state>ID</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S266</lis_member_id>
    </member>
    <member>
      <member_full>Cruz (R-TX)</member_full>
      <last_name>Cruz</last_name>
      <first_name>Ted</first_name>
      <party>R</party>
      <state>TX</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S355</lis_member_id>
    </member>
    <member>
      <member_full>Curtis (R-UT)</member_full>
      <last_name>Curtis</last_name>
      <first_name>John </first_name>
      <party>R</party>
      <state>UT</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S431</lis_member_id>
    </member>
    <member>
      <member_full>Daines (R-MT)</member_full>
      <last_name>Daines</last_name>
      <first_name>Steve</first_name>
      <party>R</party>
      <state>MT</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S375</lis_member_id>
    </member>
    <member>
      <member_full>Duckworth (D-IL)</member_full>
      <last_name>Duckworth</last_name>
      <first_name>Tammy</first_name>
      <party>D</party>
      <state>IL</state>
      <vote_cast>Nay</vote_cast>
      <lis_member_id>S386</lis_member_id>
    </member>
    <member>
      <member_full>Durbin (D-IL)</member_full>
      <last_name>Durbin</last_name>
      <first_name>Richard</first_name>
      <party>D</party>
      <state>IL</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S253</lis_member_id>
    </member>
    <member>
      <member_full>Ernst (R-IA)</member_full>
      <last_name>Ernst</last_name>
      <first_name>Joni</first_name>
      <party>R</party>
      <state>IA</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S376</lis_member_id>
    </member>
    <member>
      <member_full>Fetterman (D-PA)</member_full>
      <last_name>Fetterman</last_name>
      <first_name>John</first_name>
      <party>D</party>
      <state>PA</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S418</lis_member_id>
    </member>
    <member>
      <member_full>Fischer (R-NE)</member_full>
      <last_name>Fischer</last_name>
      <first_name>Deb</first_name>
      <party>R</party>
      <state>NE</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S357</lis_member_id>
    </member>
    <member>
      <member_full>Gallego (D-AZ)</member_full>
      <last_name>Gallego</last_name>
      <first_name>Ruben</first_name>
      <party>D</party>
      <state>AZ</state>
      <vote_cast>Nay</vote_cast>
      <lis_member_id>S432</lis_member_id>
    </member>
    <member>
      <member_full>Gillibrand (D-NY)</member_full>
      <last_name>Gillibrand</last_name>
      <first_name>Kirsten</first_name>
      <party>D</party>
      <state>NY</state>
      <vote_cast>Nay</vote_cast>
      <lis_member_id>S331</lis_member_id>
    </member>
    <member>
      <member_full>Graham (R-SC)</member_full>
      <last_name>Graham</last_name>
      <first_name>Lindsey</first_name>
      <party>R</party>
      <state>SC</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S293</lis_member_id>
    </member>
    <member>
      <member_full>Grassley (R-IA)</member_full>
      <last_name>Grassley</last_name>
      <first_name>Chuck</first_name>
      <party>R</party>
      <state>IA</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S153</lis_member_id>
    </member>
    <member>
      <member_full>Hagerty (R-TN)</member_full>
      <last_name>Hagerty</last_name>
      <first_name>Bill</first_name>
      <party>R</party>
      <state>TN</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S407</lis_member_id>
    </member>
    <member>
      <member_full>Hassan (D-NH)</member_full>
      <last_name>Hassan</last_name>
      <first_name>Maggie</first_name>
      <party>D</party>
      <state>NH</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S388</lis_member_id>
    </member>
    <member>
      <member_full>Hawley (R-MO)</member_full>
      <last_name>Hawley</last_name>
      <first_name>Josh</first_name>
      <party>R</party>
      <state>MO</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S399</lis_member_id>
    </member>
    <member>
      <member_full>Heinrich (D-NM)</member_full>
      <last_name>Heinrich</last_name>
      <first_name>Martin</first_name>
      <party>D</party>
      <state>NM</state>
      <vote_cast>Nay</vote_cast>
      <lis_member_id>S359</lis_member_id>
    </member>
    <member>
      <member_full>Hickenlooper (D-CO)</member_full>
      <last_name>Hickenlooper</last_name>
      <first_name>John</first_name>
      <party>D</party>
      <state>CO</state>
      <vote_cast>Nay</vote_cast>
      <lis_member_id>S408</lis_member_id>
    </member>
    <member>
      <member_full>Hirono (D-HI)</member_full>
      <last_name>Hirono</last_name>
      <first_name>Mazie</first_name>
      <party>D</party>
      <state>HI</state>
      <vote_cast>Nay</vote_cast>
      <lis_member_id>S361</lis_member_id>
    </member>
    <member>
      <member_full>Hoeven (R-ND)</member_full>
      <last_name>Hoeven</last_name>
      <first_name>John</first_name>
      <party>R</party>
      <state>ND</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S344</lis_member_id>
    </member>
    <member>
      <member_full>Husted (R-OH)</member_full>
      <last_name>Husted</last_name>
      <first_name>Jon</first_name>
      <party>R</party>
      <state>OH</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S438</lis_member_id>
    </member>
    <member>
      <member_full>Hyde-Smith (R-MS)</member_full>
      <last_name>Hyde-Smith</last_name>
      <first_name>Cindy</first_name>
      <party>R</party>
      <state>MS</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S395</lis_member_id>
    </member>
    <member>
      <member_full>Johnson (R-WI)</member_full>
      <last_name>Johnson</last_name>
      <first_name>Ron</first_name>
      <party>R</party>
      <state>WI</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S345</lis_member_id>
    </member>
    <member>
      <member_full>Justice (R-WV)</member_full>
      <last_name>Justice</last_name>
      <first_name>James</first_name>
      <party>R</party>
      <state>WV</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S437</lis_member_id>
    </member>
    <member>
      <member_full>Kaine (D-VA)</member_full>
      <last_name>Kaine</last_name>
      <first_name>Timothy</first_name>
      <party>D</party>
      <state>VA</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S362</lis_member_id>
    </member>
    <member>
      <member_full>Kelly (D-AZ)</member_full>
      <last_name>Kelly</last_name>
      <first_name>Mark</first_name>
      <party>D</party>
      <state>AZ</state>
      <vote_cast>Nay</vote_cast>
      <lis_member_id>S406</lis_member_id>
    </member>
    <member>
      <member_full>Kennedy (R-LA)</member_full>
      <last_name>Kennedy</last_name>
      <first_name>John</first_name>
      <party>R</party>
      <state>LA</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S389</lis_member_id>
    </member>
    <member>
      <member_full>Kim (D-NJ)</member_full>
      <last_name>Kim</last_name>
      <first_name>Andy</first_name>
      <party>D</party>
      <state>NJ</state>
      <vote_cast>Nay</vote_cast>
      <lis_member_id>S426</lis_member_id>
    </member>
    <member>
      <member_full>King (I-ME)</member_full>
      <last_name>King</last_name>
      <first_name>Angus</first_name>
      <party>I</party>
      <state>ME</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S363</lis_member_id>
    </member>
    <member>
      <member_full>Klobuchar (D-MN)</member_full>
      <last_name>Klobuchar</last_name>
      <first_name>Amy</first_name>
      <party>D</party>
      <state>MN</state>
      <vote_cast>Nay</vote_cast>
      <lis_member_id>S311</lis_member_id>
    </member>
    <member>
      <member_full>Lankford (R-OK)</member_full>
      <last_name>Lankford</last_name>
      <first_name>James</first_name>
      <party>R</party>
      <state>OK</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S378</lis_member_id>
    </member>
    <member>
      <member_full>Lee (R-UT)</member_full>
      <last_name>Lee</last_name>
      <first_name>Mike</first_name>
      <party>R</party>
      <state>UT</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S346</lis_member_id>
    </member>
    <member>
      <member_full>Lujan (D-NM)</member_full>
      <last_name>Lujan</last_name>
      <first_name>Ben</first_name>
      <party>D</party>
      <state>NM</state>
      <vote_cast>Nay</vote_cast>
      <lis_member_id>S409</lis_member_id>
    </member>
    <member>
      <member_full>Lummis (R-WY)</member_full>
      <last_name>Lummis</last_name>
      <first_name>Cynthia</first_name>
      <party>R</party>
      <state>WY</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S410</lis_member_id>
    </member>
    <member>
      <member_full>Markey (D-MA)</member_full>
      <last_name>Markey</last_name>
      <first_name>Edward</first_name>
      <party>D</party>
      <state>MA</state>
      <vote_cast>Nay</vote_cast>
      <lis_member_id>S369</lis_member_id>
    </member>
    <member>
      <member_full>Marshall (R-KS)</member_full>
      <last_name>Marshall</last_name>
      <first_name>Roger</first_name>
      <party>R</party>
      <state>KS</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S411</lis_member_id>
    </member>
    <member>
      <member_full>McConnell (R-KY)</member_full>
      <last_name>McConnell</last_name>
      <first_name>Mitch</first_name>
      <party>R</party>
      <state>KY</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S174</lis_member_id>
    </member>
    <member>
      <member_full>McCormick (R-PA)</member_full>
      <last_name>McCormick</last_name>
      <first_name>David</first_name>
      <party>R</party>
      <state>PA</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S433</lis_member_id>
    </member>
    <member>
      <member_full>Merkley (D-OR)</member_full>
      <last_name>Merkley</last_name>
      <first_name>Jeff</first_name>
      <party>D</party>
      <state>OR</state>
      <vote_cast>Nay</vote_cast>
      <lis_member_id>S322</lis_member_id>
    </member>
    <member>
      <member_full>Moody (R-FL)</member_full>
      <last_name>Moody</last_name>
      <first_name>Ashley</first_name>
      <party>R</party>
      <state>FL</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S439</lis_member_id>
    </member>
    <member>
      <member_full>Moran (R-KS)</member_full>
      <last_name>Moran</last_name>
      <first_name>Jerry</first_name>
      <party>R</party>
      <state>KS</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S347</lis_member_id>
    </member>
    <member>
      <member_full>Moreno (R-OH)</member_full>
      <last_name>Moreno</last_name>
      <first_name>Bernie</first_name>
      <party>R</party>
      <state>OH</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S434</lis_member_id>
    </member>
    <member>
      <member_full>Mullin (R-OK)</member_full>
      <last_name>Mullin</last_name>
      <first_name>Markwayne</first_name>
      <party>R</party>
      <state>OK</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S419</lis_member_id>
    </member>
    <member>
      <member_full>Murkowski (R-AK)</member_full>
      <last_name>Murkowski</last_name>
      <first_name>Lisa</first_name>
      <party>R</party>
      <state>AK</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S288</lis_member_id>
    </member>
    <member>
      <member_full>Murphy (D-CT)</member_full>
      <last_name>Murphy</last_name>
      <first_name>Christopher</first_name>
      <party>D</party>
      <state>CT</state>
      <vote_cast>Nay</vote_cast>
      <lis_member_id>S364</lis_member_id>
    </member>
    <member>
      <member_full>Murray (D-WA)</member_full>
      <last_name>Murray</last_name>
      <first_name>Patty</first_name>
      <party>D</party>
      <state>WA</state>
      <vote_cast>Nay</vote_cast>
      <lis_member_id>S229</lis_member_id>
    </member>
    <member>
      <member_full>Ossoff (D-GA)</member_full>
      <last_name>Ossoff</last_name>
      <first_name>Jon</first_name>
      <party>D</party>
      <state>GA</state>
      <vote_cast>Nay</vote_cast>
      <lis_member_id>S414</lis_member_id>
    </member>
    <member>
      <member_full>Padilla (D-CA)</member_full>
      <last_name>Padilla</last_name>
      <first_name>Alex</first_name>
      <party>D</party>
      <state>CA</state>
      <vote_cast>Nay</vote_cast>
      <lis_member_id>S413</lis_member_id>
    </member>
    <member>
      <member_full>Paul (R-KY)</member_full>
      <last_name>Paul</last_name>
      <first_name>Rand</first_name>
      <party>R</party>
      <state>KY</state>
      <vote_cast>Nay</vote_cast>
      <lis_member_id>S348</lis_member_id>
    </member>
    <member>
      <member_full>Peters (D-MI)</member_full>
      <last_name>Peters</last_name>
      <first_name>Gary</first_name>
      <party>D</party>
      <state>MI</state>
      <vote_cast>Nay</vote_cast>
      <lis_member_id>S380</lis_member_id>
    </member>
    <member>
      <member_full>Reed (D-RI)</member_full>
      <last_name>Reed</last_name>
      <first_name>John</first_name>
      <party>D</party>
      <state>RI</state>
      <vote_cast>Nay</vote_cast>
      <lis_member_id>S259</lis_member_id>
    </member>
    <member>
      <member_full>Ricketts (R-NE)</member_full>
      <last_name>Ricketts</last_name>
      <first_name>Pete</first_name>
      <party>R</party>
      <state>NE</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S423</lis_member_id>
    </member>
    <member>
      <member_full>Risch (R-ID)</member_full>
      <last_name>Risch</last_name>
      <first_name>James </first_name>
      <party>R</party>
      <state>ID</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S323</lis_member_id>
    </member>
    <member>
      <member_full>Rosen (D-NV)</member_full>
      <last_name>Rosen</last_name>
      <first_name>Jacky</first_name>
      <party>D</party>
      <state>NV</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S402</lis_member_id>
    </member>
    <member>
      <member_full>Rounds (R-SD)</member_full>
      <last_name>Rounds</last_name>
      <first_name>Mike</first_name>
      <party>R</party>
      <state>SD</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S381</lis_member_id>
    </member>
    <member>
      <member_full>Sanders (I-VT)</member_full>
      <last_name>Sanders</last_name>
      <first_name>Bernie</first_name>
      <party>I</party>
      <state>VT</state>
      <vote_cast>Nay</vote_cast>
      <lis_member_id>S313</lis_member_id>
    </member>
    <member>
      <member_full>Schatz (D-HI)</member_full>
      <last_name>Schatz</last_name>
      <first_name>Brian</first_name>
      <party>D</party>
      <state>HI</state>
      <vote_cast>Nay</vote_cast>
      <lis_member_id>S353</lis_member_id>
    </member>
    <member>
      <member_full>Schiff (D-CA)</member_full>
      <last_name>Schiff</last_name>
      <first_name>Adam</first_name>
      <party>D</party>
      <state>CA</state>
      <vote_cast>Nay</vote_cast>
      <lis_member_id>S427</lis_member_id>
    </member>
    <member>
      <member_full>Schmitt (R-MO)</member_full>
      <last_name>Schmitt</last_name>
      <first_name>Eric </first_name>
      <party>R</party>
      <state>MO</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S420</lis_member_id>
    </member>
    <member>
      <member_full>Schumer (D-NY)</member_full>
      <last_name>Schumer</last_name>
      <first_name>Charles</first_name>
      <party>D</party>
      <state>NY</state>
      <vote_cast>Nay</vote_cast>
      <lis_member_id>S270</lis_member_id>
    </member>
    <member>
      <member_full>Scott (R-FL)</member_full>
      <last_name>Scott</last_name>
      <first_name>Rick</first_name>
      <party>R</party>
      <state>FL</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S404</lis_member_id>
    </member>
    <member>
      <member_full>Scott (R-SC)</member_full>
      <last_name>Scott</last_name>
      <first_name>Tim</first_name>
      <party>R</party>
      <state>SC</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S365</lis_member_id>
    </member>
    <member>
      <member_full>Shaheen (D-NH)</member_full>
      <last_name>Shaheen</last_name>
      <first_name>Jeanne</first_name>
      <party>D</party>
      <state>NH</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S324</lis_member_id>
    </member>
    <member>
      <member_full>Sheehy (R-MT)</member_full>
      <last_name>Sheehy</last_name>
      <first_name>Tim</first_name>
      <party>R</party>
      <state>MT</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S435</lis_member_id>
    </member>
    <member>
      <member_full>Slotkin (D-MI)</member_full>
      <last_name>Slotkin</last_name>
      <first_name>Elissa</first_name>
      <party>D</party>
      <state>MI</state>
      <vote_cast>Nay</vote_cast>
      <lis_member_id>S436</lis_member_id>
    </member>
    <member>
      <member_full>Smith (D-MN)</member_full>
      <last_name>Smith</last_name>
      <first_name>Tina</first_name>
      <party>D</party>
      <state>MN</state>
      <vote_cast>Nay</vote_cast>
      <lis_member_id>S394</lis_member_id>
    </member>
    <member>
      <member_full>Sullivan (R-AK)</member_full>
      <last_name>Sullivan</last_name>
      <first_name>Dan</first_name>
      <party>R</party>
      <state>AK</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S383</lis_member_id>
    </member>
    <member>
      <member_full>Thune (R-SD)</member_full>
      <last_name>Thune</last_name>
      <first_name>John</first_name>
      <party>R</party>
      <state>SD</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S303</lis_member_id>
    </member>
    <member>
      <member_full>Tillis (R-NC)</member_full>
      <last_name>Tillis</last_name>
      <first_name>Thomas</first_name>
      <party>R</party>
      <state>NC</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S384</lis_member_id>
    </member>
    <member>
      <member_full>Tuberville (R-AL)</member_full>
      <last_name>Tuberville</last_name>
      <first_name>Tommy</first_name>
      <party>R</party>
      <state>AL</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S412</lis_member_id>
    </member>
    <member>
      <member_full>Van Hollen (D-MD)</member_full>
      <last_name>Van Hollen</last_name>
      <first_name>Chris</first_name>
      <party>D</party>
      <state>MD</state>
      <vote_cast>Nay</vote_cast>
      <lis_member_id>S390</lis_member_id>
    </member>
    <member>
      <member_full>Warner (D-VA)</member_full>
      <last_name>Warner</last_name>
      <first_name>Mark</first_name>
      <party>D</party>
      <state>VA</state>
      <vote_cast>Nay</vote_cast>
      <lis_member_id>S327</lis_member_id>
    </member>
    <member>
      <member_full>Warnock (D-GA)</member_full>
      <last_name>Warnock</last_name>
      <first_name>Raphael</first_name>
      <party>D</party>
      <state>GA</state>
      <vote_cast>Nay</vote_cast>
      <lis_member_id>S415</lis_member_id>
    </member>
    <member>
      <member_full>Warren (D-MA)</member_full>
      <last_name>Warren</last_name>
      <first_name>Elizabeth</first_name>
      <party>D</party>
      <state>MA</state>
      <vote_cast>Nay</vote_cast>
      <lis_member_id>S366</lis_member_id>
    </member>
    <member>
      <member_full>Welch (D-VT)</member_full>
      <last_name>Welch</last_name>
      <first_name>Peter</first_name>
      <party>D</party>
      <state>VT</state>
      <vote_cast>Nay</vote_cast>
      <lis_member_id>S422</lis_member_id>
    </member>
    <member>
      <member_full>Whitehouse (D-RI)</member_full>
      <last_name>Whitehouse</last_name>
      <first_name>Sheldon</first_name>
      <party>D</party>
      <state>RI</state>
      <vote_cast>Nay</vote_cast>
      <lis_member_id>S316</lis_member_id>
    </member>
    <member>
      <member_full>Wicker (R-MS)</member_full>
      <last_name>Wicker</last_name>
      <first_name>Roger</first_name>
      <party>R</party>
      <state>MS</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S318</lis_member_id>
    </member>
    <member>
      <member_full>Wyden (D-OR)</member_full>
      <last_name>Wyden</last_name>
      <first_name>Ron</first_name>
      <party>D</party>
      <state>OR</state>
      <vote_cast>Nay</vote_cast>
      <lis_member_id>S247</lis_member_id>
    </member>
    <member>
      <member_full>Young (R-IN)</member_full>
      <last_name>Young</last_name>
      <first_name>Todd</first_name>
      <party>R</party>
      <state>IN</state>
      <vote_cast>Yea</vote_cast>
      <lis_member_id>S391</lis_member_id>
    </member>
  </members>
</roll_call_vote>"""

internal val SENATE_VOTE_MENU_119_1_XML: String = """<?xml version='1.0' encoding='utf-8'?>
<vote_summary>
  <congress>119</congress>
  <session>1</session>
  <congress_year>2025</congress_year>
  <votes>
    <vote>
      <vote_number>00659</vote_number>
      <vote_date>18-Dec</vote_date>
      <issue>PN373</issue>
      <question>On the Cloture Motion
         </question>
      <result>Agreed to</result>
      <vote_tally>
        <yeas>51</yeas>
        <nays>42</nays>
      </vote_tally>
      <title>Motion to Invoke Cloture: Sara Bailey to be Director of National Drug Control Policy</title>
    </vote>
    <vote>
      <vote_number>00655</vote_number>
      <vote_date>18-Dec</vote_date>
      <en_bloc>
        <matter>
          <issue>PN416-9</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN141-12</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN499-12</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN465-1</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN345-14</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN345-13</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN345-10</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN55-2</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN624-2</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN624-1</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN379-17</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN379-16</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN416-6</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN519-2</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN445-3</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN416-12</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN55-7</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN345-12</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN465-2</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN55-26</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN55-10</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN416-2</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN379-20</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN246-6</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN560-3</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN462-3</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN462-1</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN416-5</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN518-5</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN499-10</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN499-2</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN462-4</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN141-10</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN26-24</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN445-10</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN445-7</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN445-2</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN379-4</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN246-12</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN141-27</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN141-8</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN129-17</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN129-1</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN60-17</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN60-11</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN60-4</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN22-11</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN445-5</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN466-9</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN466-8</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN466-7</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN447</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN141-5</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN499-8</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN345-8</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN246-3</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN246-11</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN60-8</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN445-11</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN416-4</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN379-15</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN379-13</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN379-11</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN141-20</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN141-25</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN246-2</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN26-47</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN60-13</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN26-26</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN379-19</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN445-1</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN379-26</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN379-22</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN416-3</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN379-9</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN379-3</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN379-18</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN246-1</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN141-14</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN416-10</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN379-21</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN55-13</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN129-6</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN55-44</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN141-39</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN379-7</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN246-13</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN246-4</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN141-26</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN141-22</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN141-16</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN129-7</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN60-12</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN55-34</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN141-2</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN26-37</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
        <matter>
          <issue>PN25-29</issue>
          <question>On the Nomination</question>
          <result>Confirmed</result>
        </matter>
      </en_bloc>
      <vote_tally>
        <yeas>53</yeas>
        <nays>43</nays>
      </vote_tally>
      <title>Confirmation: En Bloc Nominations as Provided for Under the Provisions of S. Res. 532</title>
    </vote>
    <vote>
      <vote_number>00631</vote_number>
      <vote_date>03-Dec</vote_date>
      <issue>PN520-4</issue>
      <question>On the Cloture Motion
         </question>
      <result>Agreed to</result>
      <vote_tally>
        <yeas>63</yeas>
        <nays>34</nays>
      </vote_tally>
      <title>Motion to Invoke Cloture: Susan Courtwright Rodriguez to be U.S. District Judge for the Western District of North Carolina</title>
    </vote>
    <vote>
      <vote_number>00618</vote_number>
      <vote_date>10-Nov</vote_date>
      <issue>H.R. 5371</issue>
      <question>On Passage of the Bill
         </question>
      <result>Passed</result>
      <vote_tally>
        <yeas>60</yeas>
        <nays>40</nays>
      </vote_tally>
      <title>H.R. 5371, As Amended; A bill making continuing appropriations and extensions for fiscal year 2026, and for other purposes.</title>
    </vote>
    <vote>
      <vote_number>00617</vote_number>
      <vote_date>10-Nov</vote_date>
      <issue>H.R. 5371</issue>
      <question>On the Cloture Motion
         </question>
      <result>Agreed to</result>
      <vote_tally>
        <yeas>60</yeas>
        <nays>40</nays>
      </vote_tally>
      <title>Motion to Invoke Cloture: H.R. 5371, As Amended; A bill making continuing appropriations and extensions for fiscal year 2026, and for other purposes.</title>
    </vote>
    <vote>
      <vote_number>00592</vote_number>
      <vote_date>28-Oct</vote_date>
      <issue>PN346-6</issue>
      <question>On the Nomination
         </question>
      <result>Confirmed</result>
      <vote_tally>
        <yeas>52</yeas>
        <nays>47</nays>
      </vote_tally>
      <title>Confirmation: Jordan Emery Pratt, of Florida, to be U.S. District Judge for the Middle District of Florida</title>
    </vote>
    </votes>
</vote_summary>"""

internal val LIS_TO_BIOGUIDE: Map<String, String> = mapOf(
    "S153" to "G000386",
    "S174" to "M000355",
    "S229" to "M001111",
    "S247" to "W000779",
    "S252" to "C001035",
    "S253" to "D000563",
    "S259" to "R000122",
    "S266" to "C000880",
    "S270" to "S000148",
    "S275" to "C000127",
    "S287" to "C001056",
    "S288" to "M001153",
    "S293" to "G000359",
    "S303" to "T000250",
    "S311" to "K000367",
    "S313" to "S000033",
    "S316" to "W000802",
    "S317" to "B001261",
    "S318" to "W000437",
    "S322" to "M001176",
    "S323" to "R000584",
    "S324" to "S001181",
    "S327" to "W000805",
    "S330" to "B001267",
    "S331" to "G000555",
    "S337" to "C001088",
    "S341" to "B001277",
    "S343" to "B001236",
    "S344" to "H001061",
    "S345" to "J000293",
    "S346" to "L000577",
    "S347" to "M000934",
    "S348" to "P000603",
    "S353" to "S001194",
    "S354" to "B001230",
    "S355" to "C001098",
    "S357" to "F000463",
    "S359" to "H001046",
    "S361" to "H001042",
    "S362" to "K000384",
    "S363" to "K000383",
    "S364" to "M001169",
    "S365" to "S001184",
    "S366" to "W000817",
    "S369" to "M000133",
    "S370" to "B001288",
    "S372" to "C001047",
    "S373" to "C001075",
    "S374" to "C001095",
    "S375" to "D000618",
    "S376" to "E000295",
    "S378" to "L000575",
    "S380" to "P000595",
    "S381" to "R000605",
    "S383" to "S001198",
    "S384" to "T000476",
    "S385" to "C001113",
    "S386" to "D000622",
    "S388" to "H001076",
    "S389" to "K000393",
    "S390" to "V000128",
    "S391" to "Y000064",
    "S394" to "S001203",
    "S395" to "H001079",
    "S396" to "B001243",
    "S398" to "C001096",
    "S399" to "H001089",
    "S402" to "R000608",
    "S404" to "S001217",
    "S406" to "K000377",
    "S407" to "H000601",
    "S408" to "H000273",
    "S409" to "L000570",
    "S410" to "L000571",
    "S411" to "M001198",
    "S412" to "T000278",
    "S413" to "P000145",
    "S414" to "O000174",
    "S415" to "W000790",
    "S416" to "B001319",
    "S417" to "B001305",
    "S418" to "F000479",
    "S419" to "M001190",
    "S420" to "S001227",
    "S422" to "W000800",
    "S423" to "R000618",
    "S426" to "K000394",
    "S427" to "S001150",
    "S428" to "A000382",
    "S429" to "B001299",
    "S430" to "B001303",
    "S431" to "C001114",
    "S432" to "G000574",
    "S433" to "M001243",
    "S434" to "M001242",
    "S435" to "S001232",
    "S436" to "S001208",
    "S437" to "J000312",
    "S438" to "H001104",
    "S439" to "M001244",
)
